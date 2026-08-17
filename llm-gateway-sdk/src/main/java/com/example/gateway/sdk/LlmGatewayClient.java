package com.example.gateway.sdk;

import com.example.gateway.dto.LangChainRequest;
import com.example.gateway.dto.LangChainResponse;
import com.example.gateway.dto.LangGraphRequest;
import com.example.gateway.dto.LangGraphResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * <h2>LLM 网关薄客户端 SDK</h2>
 *
 * <p>供任意项目通过 HTTP 调用 llm-gateway，无需引入网关服务端重依赖。</p>
 *
 * <p>支持接口（与 llm-gateway-core 的 LangChainController 对应）：
 * <ul>
 *   <li>POST /api/v1/chain/invoke — 非流式调用</li>
 *   <li>POST /api/v1/chain/stream — SSE 流式调用</li>
 *   <li>POST /api/v1/chain/graph/invoke — 图执行</li>
 *   <li>POST /api/v1/chain/graph/stream — 图流式执行</li>
 * </ul>
 *
 * <p>用法：
 * <pre>
 *   LlmGatewayClient client = new LlmGatewayClient("http://127.0.0.1:9095");
 *   LangChainResponse resp = client.invoke(request);
 * </pre></p>
 */
public class LlmGatewayClient {

    private static final String INVOKE_PATH = "/api/v1/chain/invoke";
    private static final String STREAM_PATH = "/api/v1/chain/stream";
    private static final String GRAPH_INVOKE_PATH = "/api/v1/chain/graph/invoke";
    private static final String GRAPH_STREAM_PATH = "/api/v1/chain/graph/stream";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public LlmGatewayClient(String baseUrl) {
        this(baseUrl, new RestTemplate(), new ObjectMapper());
    }

    public LlmGatewayClient(String baseUrl, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /** 非流式调用 */
    public LangChainResponse invoke(LangChainRequest request) {
        try {
            return restTemplate.postForObject(baseUrl + INVOKE_PATH, jsonEntity(request),
                    LangChainResponse.class);
        } catch (Exception e) {
            return LangChainResponse.fail("调用 llm-gateway 失败: " + e.getMessage(), request.getProvider());
        }
    }

    /** 流式调用：逐 token 推给 chunkConsumer */
    public void invokeStream(LangChainRequest request, Consumer<String> chunkConsumer) {
        request.setStream(true);
        consumeSse(baseUrl + STREAM_PATH, request, chunkConsumer);
    }

    /** 图执行（非流式） */
    public LangGraphResponse graphInvoke(LangGraphRequest request) {
        try {
            return restTemplate.postForObject(baseUrl + GRAPH_INVOKE_PATH, jsonEntity(request),
                    LangGraphResponse.class);
        } catch (Exception e) {
            LangGraphResponse resp = new LangGraphResponse();
            resp.setSuccess(false);
            resp.setError("调用 llm-gateway 图执行失败: " + e.getMessage());
            return resp;
        }
    }

    /** 图流式执行：事件 JSON 推给 eventConsumer */
    public void graphStream(LangGraphRequest request, Consumer<String> eventConsumer) {
        consumeSse(baseUrl + GRAPH_STREAM_PATH, request, eventConsumer);
    }

    // ── 内部工具 ──────────────────────────────────────────

    private HttpEntity<Object> jsonEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    /** 消费 SSE 流：解析 data: 行，推给 consumer */
    private void consumeSse(String url, Object body, Consumer<String> consumer) {
        try {
            restTemplate.execute(url, HttpMethod.POST,
                    req -> {
                        req.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        req.getHeaders().setAccept(java.util.List.of(MediaType.TEXT_EVENT_STREAM));
                        req.getBody().write(objectMapper.writeValueAsBytes(body));
                    },
                    (ClientHttpResponse response) -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("data:")) {
                                    String data = line.substring(5).trim();
                                    if (!data.isEmpty()) {
                                        consumer.accept(data);
                                    }
                                }
                            }
                        }
                        return null;
                    });
        } catch (Exception e) {
            consumer.accept("[调用 llm-gateway 流式失败: " + e.getMessage() + "]");
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
