# MCP Sidecar

Spring AI MCP Sidecar Service - 一个支持多服务端接入的MCP服务器和大模型聊天网关服务。

## 项目概述

本项目是一个基于Spring Boot 3.2.3和Spring AI 1.1.2构建的微服务侧车，主要包含两大核心模块：

1. **MCP服务器模块** - 支持接入多服务端能力的Model Context Protocol服务
2. **大模型聊天网关模块** - 提供WebSocket转发、应用鉴权和流量控制功能的聊天网关

## 技术栈

- **框架**: Spring Boot 3.2.3
- **AI框架**: Spring AI 1.1.2
- **协议**: MCP (Model Context Protocol)
- **通信**: WebSocket, HTTP
- **数据库**: MySQL + MyBatis Plus（预留了配置）
- **缓存**: Redis (可选)
- **消息队列**: Kafka（预留了配置）
- **监控**: Spring Actuator

## 功能特性

### 1. MCP服务器模块

支持接入多个服务端能力的MCP服务器，提供统一的工具调用接口：

#### 核心功能
- **多服务端接入**: 支持同时接入多个平台的MCP服务
- **工具管理**: 统一管理各平台提供的工具
- **协议兼容**: 支持Streamable-HTTP传输协议

#### 已实现工具
当前共 **17 个 MCP 端点、113 个工具**：

- **平台业务工具（88 个）**：人员态势、安防态势、食堂、车辆、信息发布、能源、会议、消防、周界、总览、巡检、设备、设备查询、路径规划，均通过 Platform 平台接口取数
- **知识库工具（2 个）**：RAGFlow 知识库检索
- **本地工具（3 个）**：时间计算、天气（天气当前为占位实现）
- **场景控制工具（20 个）**：19 个 UE 场景动作 + 1 个场景状态查询

完整端点与工具数见 [系统架构说明](docs/系统架构说明.md#3-mcp-端点清单)。

#### 扩展能力
- 支持动态添加新的MCP服务端
- 工具自动注册和发现
- 统一的错误处理和日志记录

### 2. 大模型聊天网关模块

提供大模型聊天的WebSocket转发功能，集成了应用鉴权和流量控制：

#### 核心功能
- **WebSocket转发**: 客户端与Python后端之间的消息转发
- **应用鉴权**: 基于Token的用户身份验证
- **流量控制**: 信号量限流机制，防止过载
- **会话管理**: 用户会话的创建、维护和清理

#### 安全特性
- **Token验证**: Bearer Token认证机制
- **用户隔离**: 基于userId的用户隔离
- **并发限制**: 可配置的并发用户数限制
- **异常处理**: 完善的错误处理和连接管理

#### 流量控制
- **信号量限流**: 使用Java Semaphore实现并发控制
- **动态调整**: 可通过配置调整并发限制
- **自动释放**: 连接断开时自动释放资源

## 快速开始

### 环境要求

- Java 17+
- Maven 3.6+
- MySQL 8.0+
- Redis (可选)
- Kafka (可选)

### 配置说明

#### 基础配置
```yaml
spring:
  application:
    name: mcp-sidecar
  ai:
    mcp:
      server:
        enabled: true
        protocol: STREAMABLE
        name: mcp-sidecar
        version: 1.0.0
```

#### MCP服务器配置
```yaml
mcp:
  internal-port: 2223            # /mcp/** 专用内网端口（server/local 均生效）
  servers:
    platform:
      baseUrl: http://<platform-host>:9500/platform
```

#### 大模型聊天配置
```yaml
llm:
  limitSize: 10                  # 最大并发用户数
  wsPath:
    chat: ws://<agent-host>:8001/chat/%s/%s
    gen_doc: ws://<agent-host>:8001/gen_doc/%s/%s
  auth:
    url: http://<platform-host>:9500/platform/llmChat/auth?token=%s&userId=%s
```

### 启动方式

```bash
# 使用Maven启动
mvn spring-boot:run

# 或者使用jar包启动
java -jar mcp-sidecar.jar
```

## API文档

### 端点总览

| 端口 | 路径 | 协议 | 说明 |
|---|---|---|---|
| 2222（公网） | `/llm/chat` | WebSocket | 智能体聊天，需 token + userId |
| 2222（公网） | `/llm/gen_doc` | WebSocket | 文档生成，需 token + userId |
| 2222（公网） | `/ws/scene` | WebSocket | UE 场景页面，`hello` 归档会话 |
| 2223（内网） | `/mcp/**` | MCP Streamable HTTP | 17 个 MCP 服务端，供 Python agent 调用 |
| 9970（容器内） | `/actuator/health` | HTTP | 健康检查 |

聊天 WebSocket 参数：`userId`、`sessionId`；token 可通过 `Authorization: Bearer <token>` 或 query `token` 传入。

### 健康检查

- **Actuator**: `http://127.0.0.1:9970/actuator/health`（容器内）
- **Swagger UI**: `/swagger-ui.html`

## 架构设计

系统由 `mcp-sidecar`（Java）和 `agentic-app`（Python）两个部署单元组成，外部依赖 Platform 平台、RAGFlow 和 LLM 服务。

```text
          浏览器聊天前端 / UE 场景页面
           WS 2222（/llm/**、/ws/**）
                        │
                        ▼
  ┌────────────────────────────────────────┐                          ┌──────────────────────────────────┐
  │ mcp-sidecar                            │  ──── WS 代理 ─────────▶ │ agentic-app :8001                │
  │ 2222 公网：/llm/**、/ws/**             │                          │ FastAPI + LangGraph              │
  │ 2223 内网：/mcp/**                     │  ◀─── MCP tools/call ─── │ MCP Client                       │
  └────────────────────────────────────────┘                          └──────────────────────────────────┘
                        │                                                              │
                        ▼   HTTP                                                       ▼   HTTPS
  ┌────────────────────────────────────────┐                          ┌──────────────────────────────────┐
  │ Platform :9500                         │                          │ LLM 服务                         │
  │ RAGFlow :9380                          │                          └──────────────────────────────────┘
  └────────────────────────────────────────┘
```

- **聊天链路**：浏览器 → 2222 `/llm/chat` → 鉴权 + 限流 → agent `:8001` → 流式返回。
- **MCP 查询链路**：agent → 2223 `/mcp/*` → sidecar 工具 → Platform / RAGFlow。
- **场景动作链路**：LLM 决定动作 → agent 调 `/mcp/scene` → sidecar 通过 `/ws/scene` 推给 UE 页面 → 页面回执 `action_result`。

完整的端口矩阵、组件职责、MCP 端点清单和四条链路的时序图见 [docs/系统架构说明.md](docs/系统架构说明.md)。

## 配置说明

### 应用鉴权配置

```yaml
llm:
  auth:
    url: http://<platform-host>:9500/platform/llmChat/auth?token=%s&userId=%s
```

### 流量控制配置

```yaml
llm:
  limitSize: 10  # 最大并发用户数
```

### MCP服务端配置

```yaml
mcp:
  internal-port: 2223   # /mcp/** 内网端口（docker 只 expose 不 publish）
  servers:
    platform:
      baseUrl: http://<platform-host>:9500/platform
    # 可以添加更多服务端
    # other:
    #   baseUrl: http://<other-host>:9301/other
```

## 开发指南

### 添加新的MCP工具

1. 在包service.mcp下创建对应的包（名字对应具体后台服务），例如 newbackend
2. 在包service.mcp.newbackend下模仿PlatformApiCallServiceImpl类创建对应平台服务的api调用类，继承 AbstractApiCallService抽象类,实现 IApiCallService接口,实现接口对应的方法。主要是调用get、post请求以及登录获取token的方法。
3. ，模仿 PlatformPathFindeMacp.java 来创建工具服务类
4. 使用 `@McpTool` 注解定义工具
5. 实现工具逻辑 
6. 在配置文件中增加新的服务平台的服务配置:
```yaml
mcp:
  servers:
    new_backend_name:
      baseUrl: xxx
```
7. 服务会自动注册到MCP服务器

### 添加新的服务端

1. 在配置文件中添加服务端配置
2. 实现对应的API调用服务
3. 注册为Spring Bean
4. 工具会自动被发现和注册

### 自定义鉴权逻辑

1. 实现 `IAuthService` 接口
2. 重写 `validateToken` 方法
3. 配置鉴权URL或使用自定义实现

## 监控与运维

### 健康检查

9970 只在容器内监听、不发布到宿主机，探活要在容器里执行：

```bash
docker exec mcp-sidecar curl -fsS http://127.0.0.1:9970/actuator/health
```

### 指标监控

- **WebSocket连接数**: 通过Actuator端点监控
- **并发用户数**: 通过流量控制器统计
- **API调用次数**: 通过MCP服务统计

### 日志配置

日志文件位置: `logs/mcp-sidecar/info.log`

## 部署说明

### Docker部署

```bash
# 构建镜像
docker build -t mcp-sidecar .

# 运行容器：只发布公网口 2222；2223（/mcp/** 内网口）和 9970（actuator）不发布
docker run -p 2222:2222 mcp-sidecar
```

### Docker Compose部署

```bash
docker-compose up -d
```