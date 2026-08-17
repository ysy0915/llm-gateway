# llm-gateway

通用 LLM 网关 —— 从 [chat-system-project](https://github.com/ysy0915/chat-system) 抽离的独立底座。

提供**多模型路由 + 统一调用 + 流式 + 熔断 + RAG + 知识图谱 + 内容安全**能力，供任意 AI 项目复用。

## 架构

```
                    HTTP                     HTTP/gRPC
业务项目 ──► llm-gateway-sdk ──► llm-gateway-core ──► 多模型提供商
                                   (独立部署)         (deepseek/qwen/doubao/...)
```

| 模块 | 说明 |
|------|------|
| `llm-gateway-models` | 纯 DTO / 枚举 / 异常（无 Spring 重依赖，供 core 与 sdk 共用） |
| `llm-gateway-core` | 可独立部署的网关服务（路由 / 调用 / 流式 / 熔断 / RAG / 图谱 / 安全） |
| `llm-gateway-sdk` | 薄客户端 SDK，供其他项目通过 HTTP 调用网关 |

## 对外接口

| 接口 | 说明 |
|------|------|
| `POST /api/v1/chain/invoke` | 非流式 LLM 调用 |
| `POST /api/v1/chain/stream` | SSE 流式调用 |
| `POST /api/v1/chain/graph/invoke` | LangGraph 图执行 |
| `POST /api/v1/chain/graph/stream` | 图流式执行 |
| `POST /api/v1/llm/admin/**` | 模型管理面（providers / models 自助接入） |
| `POST /api/v1/rag/**` | RAG 知识库管理 |
| gRPC 9095 | `LlmLangChain` / `LlmAdmin` / `LlmSafety` / `LlmLangGraph` |

## 构建

```bash
cd /Users/apple/IdeaProjects/llm-gateway
mvn clean install -DskipTests
```

## 启动（standalone 模式，零基础设施依赖）

```bash
java -jar llm-gateway-core/target/llm-gateway-core-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=standalone --server.port=9095
```

需配置 LLM API Key（环境变量）：`DEEPSEEK_API_KEY` / `QWEN_API_KEY` / `DOUBAO_API_KEY`。

完整模式（RAG / 知识图谱 / DB 管理面）需 MySQL + Redis + Milvus + Neo4j：

```bash
java -jar llm-gateway-core/target/llm-gateway-core-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod
```

## SDK 接入（其他项目）

在 `pom.xml` 引入：

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>llm-gateway-sdk</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

```java
LlmGatewayClient client = new LlmGatewayClient("http://127.0.0.1:9095");
LangChainResponse resp = client.invoke(request);          // 非流式
client.invokeStream(request, chunk -> System.out.print(chunk)); // 流式
```

## 与 chat-system-project 的关系

- 本项目的 `chat-common`（DTO / 统一响应 / 异常 / 存储 SPI / JWT）与 `chat-llm`（路由 / 调用 / 流式 / 熔断 / RAG / 图谱 / 安全）能力**抽离自此项目**
- 聊天业务耦合（会话记忆 `ConversationMemoryService`、批量导入 `BatchImportService`、用户画像实体等）**已移除**，网关保持纯 LLM 通用能力
- 原 chat-system-project **保持独立运行，不受影响**

## 目录结构

```
llm-gateway/
├── pom.xml                          # 父 pom（聚合 models + core + sdk）
├── llm-gateway-models/              # 纯模型层：DTO / 枚举 / 异常
├── llm-gateway-core/                # 核心服务（可独立部署）
│   ├── routing/                     # 多模型路由（注册中心 + DB 管理面）
│   ├── strategy/                    # 提供商策略（SPI 工厂）
│   ├── service/                     # 调用编排 + 图执行 + 内容安全
│   ├── controller/                  # LangChain 兼容 REST API + 管理面
│   ├── grpc/                        # gRPC 服务
│   ├── rag/                         # RAG 知识库（检索 + 向量化）
│   ├── metrics/ filter/ config/     # 指标 / 过滤 / 配置
│   ├── storage/                     # 存储 SPI（Graph/Vector/KV 抽象）
│   └── security/                    # JWT 认证
└── llm-gateway-sdk/                 # 薄客户端 SDK
```
