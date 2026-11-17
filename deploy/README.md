# deploy/ —— 运行时目录（jar + 配置同目录）

这个目录整体挂载到容器的 `/app`（镜像的工作目录），所以里面的东西就是容器启动时看到的东西。

```
deploy/
├── mcp-sidecar.jar          # 编译产物，容器跑的就是它
├── application-server.yml   # profile=server 的配置（已改为 ${ENV} 占位，不入库）
├── docker-start.sh          # 启动脚本（java -jar mcp-sidecar.jar）
└── logs/                    # 运行日志（容器内 /app/logs，第一次启动自动创建）
```

## 凭证放哪里

真实凭据统一放项目根目录的 `.env`，该文件已被 `.gitignore` 忽略：

```bash
cp .env.example .env
# 编辑 .env，填入平台、RAGFlow、数据库、Redis、SSL 等值
```

- Docker：Compose 自动读取同目录 `.env`，通过 `mcp-sidecar` 的 `environment` 注入容器；
- 本地 IDE：`application.yml` 里的 `spring.config.import` 会读取项目根目录 `.env`；
- `deploy/application-server.yml` 只保留 `${...}` 占位，本身不再包含明文，也不再提交到 Git。

## MCP 内网端点鉴权（SEC-08）

`/mcp/**` 要求应用层 API Key，`MCP_API_KEY` 未配置时**拒绝所有请求（401）**：

```bash
# 生成一个 key，写进根目录 .env
openssl rand -hex 32          # 追加：MCP_API_KEY=<生成值>
```

- `docker-compose.yml` 会把同一个 `MCP_API_KEY` 同时注入 `mcp-sidecar` 和 `agentic-app`；
  Python 侧 MCP client 必须带上 `Authorization: Bearer <key>` 或 `X-MCP-API-Key: <key>`。
- 容器内 `MCP_INTERNAL_ADDRESS` 固定为 `0.0.0.0`（compose 已注入），否则 `agentic-app`
  无法通过 `mcp-sidecar:2223` 访问；2223 不发布到宿主机，不代表对公网开放。
- 本地非容器运行时保持默认 `127.0.0.1`，2223 只在回环地址监听。

## 改了东西怎么生效

```bash
# 只改配置
vim deploy/application-server.yml
docker compose restart mcp-sidecar

# 重新打包后换 jar（不用重建镜像）
mvn clean package
cp target/mcp-sidecar.jar deploy/
docker compose restart mcp-sidecar

# 换 profile（默认 server）
SPRING_PROFILES_ACTIVE=local docker compose up -d mcp-sidecar
```

## 配置生效顺序

Spring Boot 默认配置位置（后者覆盖前者）：

```
classpath:/ → classpath:/config/ → file:./ → file:./config/
```

`application-server.yml` 放在 jar 旁边（`file:./`）就已经优先于 jar 内那份了，不需要再建 `config/` 子目录。
如果你更习惯 `config/` 结构，把它挪到 `deploy/config/application-server.yml` 也一样能读到。

## 注意

- `deploy/` 里是运行时产物；`.gitignore` 已忽略 `deploy/*.jar`、`deploy/logs/` 和 `deploy/application-server.yml`。
- 真实凭据只放项目根目录 `.env`，不要写回 `deploy/application-server.yml` 或 `src/main/resources`。
- 别把 `deploy/` 清空或删掉 `mcp-sidecar.jar`：那时容器里 `/app` 就是空的，`java -jar` 会直接报找不到 jar。
