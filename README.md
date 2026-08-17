<div align="center">

# llm-gateway

**通用 LLM 网关 · General-Purpose LLM Gateway**

多模型路由 · 统一调用 · 流式 · 熔断 · RAG · 知识图谱 · 内容安全

从 [chat-system](https://github.com/ysy0915/chat-system) 生产环境抽离的独立底座，供任意 AI 项目复用。

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

</div>

---

## ✨ 核心特性

- **多模型统一路由**：DeepSeek / Qwen / Doubao / OpenAI / Ollama 等提供商统一接入，自动路由、故障转移
- **统一调用协议**：LangChain 兼容接口（invoke / stream），REST + gRPC 双协议
- **流式输出**：SSE 流式逐 token 返回，支持思考链（reasoning）剥离透传
- **弹性工程**：Resilience4j 熔断、限流、超时、重试，多级降级
- **RAG 检索增强**：向量化 + 语义检索（Milvus），知识库管理
- **知识图谱**：三元组抽取 + 图存储（Neo4j），图查询与流式执行
- **内容安全**：本地词库 + 阿里云内容安全双道防线，fail-close
- **管理面**：模型提供商 / 模型自助接入（DB 管理面，60s 自动刷新，改配置无需重启）
- **可观测**：Prometheus 指标 + 全链路 traceId + 结构化日志

## 🏗 架构

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

## 🚀 快速开始

### 1. 构建

```bash
mvn clean install -DskipTests
```

### 2. 启动（standalone 模式，零基础设施依赖）

```bash
java -jar llm-gateway-core/target/llm-gateway-core-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=standalone --server.port=9095
```

配置 LLM API Key（环境变量）：`DEEPSEEK_API_KEY` / `QWEN_API_KEY` / `DOUBAO_API_KEY`。

### 3. 验证

非流式调用：

```bash
curl -X POST http://127.0.0.1:9095/api/v1/chain/invoke \
  -H "Content-Type: application/json" \
  -d '{"provider":"deepseek","model":"deepseek-chat","messages":[{"role":"user","content":"你好"}]}'
```

流式调用（SSE）：

```bash
curl -N -X POST http://127.0.0.1:9095/api/v1/chain/stream \
  -H "Content-Type: application/json" \
  -d '{"provider":"deepseek","model":"deepseek-chat","messages":[{"role":"user","content":"介绍一下你自己"}]}'
```

### 完整模式（RAG / 知识图谱 / DB 管理面）

需 MySQL + Redis + Milvus + Neo4j：

```bash
java -jar llm-gateway-core/target/llm-gateway-core-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod
```

## 📡 对外接口

### REST API

| 接口 | 说明 |
|------|------|
| `POST /api/v1/chain/invoke` | 非流式 LLM 调用 |
| `POST /api/v1/chain/stream` | SSE 流式调用 |
| `POST /api/v1/chain/graph/invoke` | LangGraph 图执行 |
| `POST /api/v1/chain/graph/stream` | 图流式执行 |
| `POST /api/v1/llm/admin/**` | 模型管理面（providers / models 自助接入） |
| `POST /api/v1/rag/**` | RAG 知识库管理 |

### gRPC（端口 9095）

| 服务 | 说明 |
|------|------|
| `LlmLangChain` | LLM 调用 |
| `LlmAdmin` | 管理面 |
| `LlmSafety` | 内容安全 |
| `LlmLangGraph` | 图执行 |

## 📦 SDK 接入

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

// 非流式
LangChainResponse resp = client.invoke(request);

// 流式
client.invokeStream(request, chunk -> System.out.print(chunk));
```

## 🗂 目录结构

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

## 🔗 与 chat-system 的关系

- 本项目能力从 [chat-system](https://github.com/ysy0915/chat-system) 抽离而来：`chat-common`（DTO / 统一响应 / 异常 / 存储 SPI / JWT）+ `chat-llm`（路由 / 调用 / 流式 / 熔断 / RAG / 图谱 / 安全）
- 聊天业务耦合（会话记忆、批量导入、用户画像实体等）**已移除**，网关保持纯 LLM 通用能力
- 原 chat-system **保持独立运行，不受影响**

## 📄 License

[Apache License 2.0](LICENSE)
