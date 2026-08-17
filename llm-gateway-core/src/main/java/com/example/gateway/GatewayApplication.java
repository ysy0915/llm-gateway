package com.example.gateway;

import com.example.gateway.config.GlobalExceptionHandler;
import com.example.gateway.config.LlmConfigProperties;
import com.example.gateway.security.JwtUtil;
import com.example.gateway.storage.StorageRegistry;
import com.example.gateway.service.DirectLLMClient;
import com.example.gateway.util.BaseUrlResolver;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * <h2>llm-gateway 启动类</h2>
 *
 * <p>LLM 多模型路由 + LangGraph 图引擎，端口 9095，gRPC 9195。</p>
 *
 * <h3>Standalone 模式（独立运行）</h3>
 * <pre>
 *   java -jar llm-gateway-core.jar --spring.profiles.active=standalone
 * </pre>
 * 不依赖 MySQL/Redis/RabbitMQ/Neo4j/Milvus，只配 LLM API Key 即可使用：
 * <ul>
 *   <li>POST /api/v1/chain/invoke — 非流式调用</li>
 *   <li>POST /api/v1/chain/stream — SSE 流式调用</li>
 * </ul>
 *
 * <h3>完整模式（RAG/知识图谱/DB 管理面）</h3>
 * <pre>
 *   java -jar llm-gateway-core.jar --spring.profiles.active=prod
 * </pre>
 * 需要 MySQL + Redis + Milvus + Neo4j，功能全开。
 *
 * <p>{@link EnableAspectJAutoProxy} 开启 AOP 代理以支持
 * Resilience4j 的 {@code @CircuitBreaker} / {@code @Retry} / {@code @RateLimiter}
 * 等注解。</p>
 *
 * <p>chat-common 的 MyBatis Mapper（{@code com.example.gateway.repository}）、本模块
 * 遗留 RAG Mapper（{@code com.example.gateway.rag.legacy}）与模型管理面
 * Mapper（{@code com.example.gateway.routing.db}）均位于默认扫描路径之外，
 * 公共组件（LlmConfigProperties / DirectLLMClient / BaseUrlResolver）也需显式注册。</p>
 *
 * <p><b>MapperScan 条件化</b>：无 DataSource（standalone 模式）时跳过 Mapper 扫描，
 * 避免启动报错。通过内嵌 {@code MapperScanConfig} + {@code @ConditionalOnProperty}
 * 实现。</p>
 */
@SpringBootApplication
@EnableAspectJAutoProxy
@EnableScheduling
@Import({LlmConfigProperties.class, DirectLLMClient.class, BaseUrlResolver.class, JwtUtil.class,
        GlobalExceptionHandler.class, StorageRegistry.class})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    /**
     * 有 DataSource 时才启用 MapperScan（避免 standalone 模式无 DB 启动失败）。
     * 由 app.mapper-scan.enabled 控制（默认 true，standalone 配置中显式关闭）。
     * <p>仅扫描带 {@code @Mapper} 注解的接口：避免将 rag.legacy 包下无注解的
     * 领域接口（如 {@code MemoryKVStore}/{@code VectorStoreLegacy}/{@code UserFactMemory}）
     * 误注册为 MyBatis Mapper bean，导致与 @Component 实现类型冲突
     * （如 ConversationMemoryService 注入 MemoryKVStore 时发现 redisMemoryKVStore + memoryKVStore 两个候选）。</p>
     */
    @org.springframework.context.annotation.Configuration
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.mapper-scan.enabled", havingValue = "true", matchIfMissing = true)
    @MapperScan(annotationClass = Mapper.class, basePackages = {
            "com.example.gateway.repository",
            "com.example.gateway.rag.legacy",
            "com.example.gateway.routing.db"})
    public static class MapperScanConfig {}
}

