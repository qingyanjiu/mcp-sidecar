# deploy/ —— 运行时目录（jar + 配置同目录）

这个目录整体挂载到容器的 `/app`（镜像的工作目录），所以里面的东西就是容器启动时看到的东西。

```
deploy/
├── mcp-sidecar.jar          # 编译产物，容器跑的就是它
├── application-server.yml   # profile=server 的配置，和 jar 同目录 → Spring 按 file:./ 读取，优先于 jar 内的同名文件
├── docker-start.sh          # 启动脚本（java -jar mcp-sidecar.jar）
└── logs/                    # 运行日志（容器内 /app/logs，第一次启动自动创建）
```

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

- `deploy/` 里是运行时产物 + 真实密钥，`.gitignore` 已忽略 `deploy/*.jar` 和 `deploy/logs/`；不想把配置也提交，就再加一行 `deploy/`。
- 别把 `deploy/` 清空或删掉 `mcp-sidecar.jar`：那时容器里 `/app` 就是空的，`java -jar` 会直接报找不到 jar。
