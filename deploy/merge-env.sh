#!/usr/bin/env bash
# ============================================================================
# 把「部署需要的环境变量」幂等地合并进 .env —— 只补缺失的键，绝不覆盖已有值。
# ----------------------------------------------------------------------------
# 为什么需要它：docker compose 只读仓库根目录这一个 .env，而 .env 由
# env.docker.example（非敏感部分）和 .env.example（敏感部分）组合而成，
# 手工合并容易漏键。漏 MCP_API_KEY 会让 /mcp/** 全部 401；漏 KB_* 容器照常启动，
# 但检索会静默退回纯向量。
#
# 键的来源（自动读取，不再维护第三份硬编码清单）：
#   1) .env.example        敏感信息部分，值优先（同名键覆盖非敏感部分）
#   2) env.docker.example  非敏感配置，补 .env.example 没覆盖的键
#   3) docker-compose.yml  里所有 ${VAR...} 引用（兜底，取不到值时写空）
# compose 以后新增变量，重跑本脚本就能补上，不会再漂移。
#
# 首次部署更直观的做法是按「先非敏感、后敏感」直接组合两份模板（见部署说明）；
# 本脚本用于在已有 .env 上增量补缺，默认预演不改文件。
#
# 用法（在 mcp-sidecar 仓库根目录执行）：
#   bash deploy/merge-env.sh            # 预演：只列出会补哪些键，不改文件
#   bash deploy/merge-env.sh --write    # 真正写入（写之前自动备份 .env.bak）
#   ENV_FILE=/path/to/.env bash deploy/merge-env.sh --write   # 指定别的文件
#
# 安全约定：
#   - 只读键名，不打印任何值；写入的值全部来自仓库里的 *.example 模板
#     （compose 兜底出来的键写空，交给 compose 里的默认值）；
#   - 已存在的键一律保留原值（包括你手填的密钥）；
#   - 追加前在文件末尾补一个空行，注释都单独成行（dotenv 的坑）。
# ============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
WRITE=0
if [ "${1:-}" = "--write" ]; then
  WRITE=1
fi

if [ ! -f "$ENV_FILE" ]; then
  echo "找不到 $ENV_FILE" >&2
  echo "首次部署先用 .env.example + env.docker.example 组合出 .env（再执行本脚本补漏）" >&2
  exit 1
fi

has_key() { grep -qE "^[[:space:]]*$1[[:space:]]*=" "$ENV_FILE"; }
current_value() {
  grep -E "^[[:space:]]*$1[[:space:]]*=" "$ENV_FILE" 2>/dev/null | tail -1 |
    sed -E "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//" || true
}

# 把唯一键收集进临时文件，实际值后面再读，避免在 shell 里存密钥副本。
raw="$(mktemp "${TMPDIR:-/tmp}/merge-env.raw.XXXXXX")"
uniq="$(mktemp "${TMPDIR:-/tmp}/merge-env.uniq.XXXXXX")"
missing="$(mktemp "${TMPDIR:-/tmp}/merge-env.missing.XXXXXX")"
trap 'rm -f "$raw" "$uniq" "$missing"' EXIT

# ---- 1. 两份 example：只取未注释的 KEY=VALUE 行 -----------------------------
parse_example() {
  local file="$1" src
  [ -f "$file" ] || return 0
  src="$(basename "$file")"
  awk -v src="$src" '
    {
      line = $0
      sub(/\r$/, "", line)
      if (line ~ /^[[:space:]]*[A-Za-z_][A-Za-z0-9_]*[[:space:]]*=/) {
        sub(/^[[:space:]]*/, "", line)
        key = line
        sub(/[[:space:]]*=.*/, "", key)
        val = line
        sub(/^[^=]*=/, "", val)
        sub(/^[[:space:]]*/, "", val)
        printf "%s\t%s\t%s\n", key, src, val
      }
    }
  ' "$file" >> "$raw"
}

parse_example "$ROOT_DIR/.env.example"
parse_example "$ROOT_DIR/env.docker.example"

# ---- 2. compose 里引用、但两份 example 都没写的变量，兜底补空值 ----------------
if [ -f "$ROOT_DIR/docker-compose.yml" ]; then
  grep -oE '\$\{[A-Za-z_][A-Za-z0-9_]*' "$ROOT_DIR/docker-compose.yml" |
    sed 's/^\${//' | sort -u |
    awk '{ printf "%s\tcompose\t\n", $0 }' >> "$raw"
fi

# 同一个键保留第一次出现的值（.env.example > env.docker.example > compose）
awk -F'\t' '!seen[$1]++' "$raw" > "$uniq"

# ---- 3. 只保留 .env 里还没有的键 ---------------------------------------------
: > "$missing"
while IFS=$'\t' read -r key src value; do
  [ -n "$key" ] || continue
  if ! has_key "$key"; then
    printf '%s\t%s\t%s\n' "$key" "$src" "$value" >> "$missing"
  fi
done < "$uniq"

missing_count="$(wc -l < "$missing" | tr -d '[:space:]')"
template_count="$(awk -F'\t' '$2 != "compose"' "$missing" | wc -l | tr -d '[:space:]')"
compose_count=$((missing_count - template_count))

# 取「合并后」的值：已在 .env 里的读文件，缺失的读候选值。只用于告警，不回显。
effective_value() {
  local key="$1" val
  if has_key "$key"; then
    current_value "$key"
    return
  fi
  val="$(awk -F'\t' -v k="$key" '$1 == k { print $3; exit }' "$missing" 2>/dev/null || true)"
  printf '%s' "$val"
}

warn() { echo "！警告：$*" >&2; }

run_warnings() {
  local rag kb_url kb_key mcp_dir agentic_dir
  rag="$(effective_value MCP_RAGFLOW_BASE_URL)"
  case "$rag" in
    *localhost*|*127.0.0.1*)
      warn "MCP_RAGFLOW_BASE_URL 指向 localhost/127.0.0.1。"
      echo "  这是 sidecar 容器自己的地址，/mcp/knowledge 的检索会一直 Connection refused。" >&2
      echo "  改成容器内可达的地址：http://ragflow:9380 或 http://host.docker.internal:9380" >&2
      echo "  （Linux 还要给 mcp-sidecar 加 extra_hosts: [\"host.docker.internal:host-gateway\"]）" >&2
      echo >&2
      ;;
    "")
      warn "MCP_RAGFLOW_BASE_URL 为空，sidecar 的知识库工具会直接报未配置。"
      echo >&2
      ;;
  esac

  if [ -z "$(effective_value MCP_API_KEY)" ]; then
    warn "MCP_API_KEY 为空，/mcp/** 的所有请求都会被 401 拒绝（SEC-08 默认拒绝）。"
    echo "  生成：openssl rand -hex 32，然后同步写进 agentic-app 的 MCP_API_KEY。" >&2
    echo >&2
  fi

  if [ -z "$(effective_value MCP_PLATFORM_BASE_URL)" ]; then
    warn "MCP_PLATFORM_BASE_URL 为空，sidecar 的平台工具拿不到上游地址。"
    echo >&2
  fi

  kb_url="$(effective_value KB_BASE_URL)"
  kb_key="$(effective_value KB_API_KEY)"
  if [ -n "$kb_url" ] && [ -z "$kb_key" ]; then
    warn "KB_BASE_URL 已配置但 KB_API_KEY 为空，知识库直连会鉴权失败。"
    echo >&2
  elif [ -z "$kb_url" ] && [ -n "$kb_key" ]; then
    warn "KB_API_KEY 已配置但 KB_BASE_URL 为空；两者都为空才会回落到 sidecar 的 /mcp/knowledge。"
    echo >&2
  fi

  if [ -z "$(effective_value OPENAI_API_KEY)" ] && [ -z "$(effective_value LLM_API_KEY)" ]; then
    warn "OPENAI_API_KEY / LLM_API_KEY 都为空，agentic-app 的模型能力不可用。"
    echo >&2
  fi

  mcp_dir="$(effective_value MCP_CODE_DIR)"
  if [ -n "$mcp_dir" ] && [ "$mcp_dir" != "$ROOT_DIR" ]; then
    warn "MCP_CODE_DIR=$mcp_dir 与本仓库 $ROOT_DIR 不一致，compose 会挂载错目录。"
    echo >&2
  fi

  agentic_dir="$(effective_value AGENTIC_CODE_DIR)"
  if [ -n "$agentic_dir" ] && [ ! -d "$agentic_dir" ]; then
    warn "AGENTIC_CODE_DIR=$agentic_dir 不存在，agentic-app 容器会挂载到空目录。"
    echo >&2
  fi
}

if [ "$missing_count" -eq 0 ]; then
  echo "无需改动：$ENV_FILE 已经覆盖了 example / compose 里的全部键。"
  run_warnings
  exit 0
fi

echo "模板中存在、但当前 .env 未写的键：$template_count 个（只显示键名和来源）"
if [ "$template_count" -gt 0 ]; then
  awk -F'\t' '$2 != "compose" { printf "  %-36s （来自 %s）\n", $1, $2 }' "$missing"
fi
if [ "$compose_count" -gt 0 ]; then
  echo
  echo "仅被 compose 引用、不写也会用默认值的可选键：$compose_count 个"
  awk -F'\t' '$2 == "compose" { printf "  %s\n", $1 }' "$missing"
fi
echo

run_warnings

if [ "$WRITE" -ne 1 ]; then
  echo
  echo "以上只是列出差异，不会修改 .env。"
  echo "预演模式，未修改文件。确认无误后执行： bash deploy/merge-env.sh --write"
  exit 0
fi

cp "$ENV_FILE" "$ENV_FILE.bak"
{
  # 末尾没有换行就先补一个，再空一行，保证追加内容不和最后一行粘连
  [ -n "$(tail -c 1 "$ENV_FILE" || true)" ] && echo
  echo
  echo "# ---- 以下由 deploy/merge-env.sh 补齐（$(date '+%Y-%m-%d %H:%M')） ----"
  echo "# 值取自 env.docker.example / .env.example；*_CODE_DIR、KB_BASE_URL 等机器相关项请核对"
  while IFS=$'\t' read -r key _src value; do
    printf '%s=%s\n' "$key" "$value"
  done < "$missing"
} >> "$ENV_FILE"

echo
echo "已写入 ${ENV_FILE}（原文件备份为 ${ENV_FILE}.bak）"
echo "生效方式： docker compose up -d    # restart 不会重读 .env 的新值"
