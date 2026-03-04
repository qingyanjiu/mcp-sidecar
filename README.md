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
- **路径规划服务** (`path_find_service:getPathFindGridList`)
  - 查询路径规划配置列表
  - 返回空间内墙体矩阵数据（1可通过，0不可通过）
  
- **时间服务** (`time_service:getCurrentDate`)
  - 获取当前日期

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
  servers:
    platform:
      baseUrl: http://localhost:9300/platform
```

#### 大模型聊天配置
```yaml
llm:
  chat:
    limitSize: 10  # 最大并发用户数
    wsBasePath: ws://127.0.0.1:8001/agentic_rag_query/%s/%s
  auth:
    url: http://localhost:9300/platform/llmChat/auth?token=%s&userId=%s
```

### 启动方式

```bash
# 使用Maven启动
mvn spring-boot:run

# 或者使用jar包启动
java -jar mcp-sidecar.jar
```

## API文档

### MCP服务端点

- **WebSocket**: `/llm/chat`
  - 支持的参数: `userId`, `sessionId`
  - 请求头: `Authorization: Bearer <token>`

### 健康检查

- **Actuator**: `/actuator/health`
- **Swagger UI**: `/swagger-ui.html`

## 架构设计

### 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                    客户端层                                │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  聊天网关层                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │  鉴权拦截器  │  │ 流量控制器  │  │ WS转发器     │     │
│  └─────────────┘  └─────────────┘  └─────────────┘     │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  MCP服务层                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │ 路径规划    │  │ 时间服务    │  │ 其他工具    │     │
│  └─────────────┘  └─────────────┘  └─────────────┘     │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  后端服务层                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │ Platform    │  │ Python      │  │ 其他服务    │     │
│  │   服务      │  │   Agent     │  │             │     │
│  └─────────────┘  └─────────────┘  └─────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

### 核心组件

#### 1. WebSocket转发流程
1. 客户端连接WebSocket网关
2. 鉴权拦截器验证Token和用户身份
3. 流量控制器检查并发限制
4. 建立与Python后端的WebSocket连接
5. 双向消息转发

#### 2. MCP工具调用流程
1. 工具请求到达MCP服务器
2. 根据工具名称路由到对应服务端
3. 调用具体平台的API
4. 返回处理结果

## 配置说明

### 应用鉴权配置

```yaml
llm:
  auth:
    url: http://localhost:9300/platform/llmChat/auth?token=%s&userId=%s
```

### 流量控制配置

```yaml
llm:
  chat:
    limitSize: 10  # 最大并发用户数
```

### MCP服务端配置

```yaml
mcp:
  servers:
    platform:
      baseUrl: http://localhost:9300/platform
    # 可以添加更多服务端
    # other:
    #   baseUrl: http://localhost:9301/other
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

```bash
curl http://localhost:9970/actuator/health
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

# 运行容器
docker run -p 2222:2222 -p 9970:9970 mcp-sidecar
```

### Docker Compose部署

```bash
docker-compose up -d
```