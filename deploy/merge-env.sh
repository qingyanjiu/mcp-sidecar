#!/usr/bin/env bash
# ============================================================================
# 把「部署需要的环境变量」幂等地合并进 .env —— 只补缺失的键，绝不覆盖已有值。
# ----------------------------------------------------------------------------
# 为什么需要它：docker-compose 只读仓库根目录这一个 .env，
# 而 .env.example（sidecar 侧）和 env.docker.example（整套栈）分成了两份文档，
# 手工合并容易漏键；漏了 KB_* 或 REDIS_* 时容器起来但功能静默不生效。
#
# 用法（在 mcp-sidecar 仓库根目录执行）：
#   bash deploy/merge-env.sh            # 预演：只列出会补哪些键，不改文件
#   bash deploy/merge-env.sh --write    # 真正写入（写之前自动备份 .env.bak）
#   ENV_FILE=/path/to/.env bash deploy/merge-env.sh --write   # 指定别的文件
#
# 安全约定：
#   - 只读键名，不打印任何值；本脚本的输出可以直接贴给他人；
#   - 已存在的键一律保留原值（包括你手填的密钥）；
#   - 追加前在文件末尾补一个空行，注释都单独成行（python-dotenv 的坑）。
# ============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
WRITE=0
[ "${1:-}" = "--write" ] && WRITE=1

if [ ! -f "$ENV_FILE" ]; then
  echo "找不到 $ENV_FILE" >&2
  echo "首次部署可以先： cp .env.example .env   （再合并 env.docker.example 里的 AGENTIC_* 等）" >&2
  exit 1
fi

# 需要保证存在的键（值只用于缺失时写入；已有的不动）
# 格式：KEY=VALUE|分组说明
ENTRIES='
# ---- sidecar：platform / RAGFlow / 登录（缺了对应 profile 拿不到地址） ----
MCP_PLATFORM_BASE_URL=|platform
LLM_AUTH_URL=|platform
LOCAL_MCP_PLATFORM_BASE_URL=|local-profile
LOCAL_MCP_RAGFLOW_BASE_URL=|local-profile
LOCAL_MCP_RAGFLOW_API_KEY=|local-profile
LOCAL_LLM_AUTH_URL=|local-profile
PLATFORM_LOGIN_USERNAME=|login
PLATFORM_LOGIN_PASSWORD=|login
PLATFORM_LOGIN_TYPE=1|login
PLATFORM_CAPTCHA_ID=|login
PLATFORM_CAPTCHA_CODE=|login
PLATFORM_ALLOW_PLAINTEXT_LOGIN=false|login
SPRING_PROFILES_ACTIVE=server|runtime
# ---- Redis（compose 转成 SPRING_DATA_REDIS_* 注入） ----
REDIS_HOST=localhost|redis
REDIS_PORT=6379|redis
REDIS_PASSWORD=|redis
REDIS_DATABASE=10|redis
# ---- DB / SSL / Kafka ----
DB_URL=|infra
DB_USERNAME=|infra
DB_PASSWORD=|infra
SSL_KEY_STORE=|infra
SSL_KEY_PASSWORD=|infra
KAFKA_BOOTSTRAP_SERVERS=|infra
# ---- 跨域白名单 ----
SECURITY_ALLOWED_ORIGINS=|security
# ---- agentic-app：知识库直连平台检索接口 ----
KB_BASE_URL=|kb
KB_API_KEY=|kb
KB_RETRIEVAL_PATH=/v1/api_key/datasets/draft|kb
KB_DATASET_IDS=|kb
KB_TOP_K=5|kb
KB_SCORE_THRESHOLD=|kb
KB_RERANK_ENABLE=1|kb
KB_RERANK_PROVIDER=tong_yi|kb
KB_RERANK_MODEL=gte-rerank|kb
KB_TIMEOUT=20|kb
KB_SEGMENT_MAX_CHARS=800|kb
# ---- agentic-app：会话 / 缓存 / 灰度 ----
SESSION_TTL_SECONDS=1800|app
DEVICE_CACHE_TTL=|app
PREWARM_ON_STARTUP=|app
AGENT_MODE=auto|app
LOOP_ROLLOUT_PERCENT=|app
LOOP_ROLLOUT_USERS=|app
LOOP_DISCOVER=1|app
'

has_key() { grep -qE "^[[:space:]]*$1[[:space:]]*=" "$ENV_FILE"; }
current_value() {
  grep -E "^[[:space:]]*$1[[:space:]]*=" "$ENV_FILE" | tail -1 | sed -E "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//"
}

missing=()
while IFS= read -r line; do
  [ -z "$line" ] && continue
  case "$line" in \#*) continue ;; esac
  key="${line%%=*}"
  if has_key "$key"; then continue; fi
  missing+=("$line")
done <<< "$ENTRIES"

# RAGFlow 可达性检查：值只做归类，不回显
if has_key MCP_RAGFLOW_BASE_URL; then
  ragflow="$(current_value MCP_RAGFLOW_BASE_URL)"
  case "$ragflow" in
    *localhost*|*127.0.0.1*)
      echo "！警告：MCP_RAGFLOW_BASE_URL 指向 localhost/127.0.0.1。" >&2
      echo "  这是 sidecar 容器自己的地址，/mcp/knowledge 的检索会一直 Connection refused。" >&2
      echo "  改成容器内可达的地址：http://ragflow:9380 或 http://host.docker.internal:9380" >&2
      echo "  （Linux 还要给 mcp-sidecar 加 extra_hosts: [\"host.docker.internal:host-gateway\"]）" >&2
      echo >&2
      ;;
    "") echo "！警告：MCP_RAGFLOW_BASE_URL 为空，sidecar 的知识库工具会直接报未配置。" >&2 ; echo >&2 ;;
  esac
fi

if [ "${#missing[@]}" -eq 0 ]; then
  echo "无需改动：$ENV_FILE 里这些键都已存在。"
  exit 0
fi

echo "将补齐 ${#missing[@]} 个缺失键（只显示键名，不显示值）："
printf '  %s\n' "${missing[@]%%=*}"

if [ "$WRITE" -ne 1 ]; then
  echo
  echo "预演模式，未修改文件。确认无误后执行： bash deploy/merge-env.sh --write"
  exit 0
fi

cp "$ENV_FILE" "$ENV_FILE.bak"
{
  # 末尾没有换行就先补一个，再空一行，保证追加内容不和最后一行粘连
  [ -n "$(tail -c 1 "$ENV_FILE" || true)" ] && echo
  echo
  echo "# ---- 以下由 deploy/merge-env.sh 补齐（$(date '+%Y-%m-%d %H:%M')） ----"
  for entry in "${missing[@]}"; do
    # ENTRIES 里的 "KEY=VALUE|分组"：只把 | 前半段写进文件，后半段是给人看的分组标记
    printf '%s\n' "${entry%%|*}"
  done
} >> "$ENV_FILE"

echo
echo "已写入 $ENV_FILE（原文件备份为 $ENV_FILE.bak）"
echo "生效方式： docker compose up -d    # restart 不会重读 .env 的新值"
