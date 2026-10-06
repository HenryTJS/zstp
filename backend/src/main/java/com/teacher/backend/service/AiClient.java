package com.teacher.backend.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Shared AI HTTP client for all AI-related services.
 * Provides chatJson() and chatText() methods to call OpenAI-compatible APIs.
 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    protected final String apiKey;
    protected final String baseUrl;
    protected final String model;
    /**
     * 可选的 temperature 配置。
     * 为 null 时请求体不携带 temperature 字段，由服务端使用自身默认值。
     * 这样可兼容仅允许特定取值（如部分推理模型只允许 temperature=1）的模型，
     * 也可通过环境变量 OPENAI_TEMPERATURE 显式指定。
     */
    protected final Double temperature;

    /**
     * AI 结果缓存时长（毫秒），<=0 表示不缓存。
     * 前端打开知识点页面时会并发发起多个内容相同的 AI 请求，
     * 缓存可避免重复消耗配额（对低 RPM 账号尤其重要）。
     */
    protected final long cacheTtlMs;

    /**
     * 两次 AI 请求之间的最小间隔（毫秒），<=0 表示不限流。
     * 用于适配配额较低的账号（例如 Moonshot 免费档 RPM 仅 3）。
     */
    protected final long minIntervalMs;

    /** 遇到 429（限流）或 5xx 时的最大重试次数。 */
    protected final int maxRetries;

    /** 重试基础退避时长（毫秒），按 2 的幂次递增。 */
    protected final long retryBackoffMs;

    /** 响应缓存：key 为请求指纹，value 为已解析结果。 */
    private final Map<String, CacheEntry> responseCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** 按请求指纹加锁，使并发的相同请求只真正调用一次 AI。 */
    private final Map<String, Object> keyLocks = new java.util.concurrent.ConcurrentHashMap<>();

    /** 限流用的单调时钟基准。 */
    private final Object throttleLock = new Object();
    private long lastRequestAtMs = 0L;

    /** 缓存条目，携带过期时间。 */
    private record CacheEntry(Object value, long expiresAtMs) {}

    public AiClient(
        @Value("${OPENAI_API_KEY:}") String apiKey,
        @Value("${OPENAI_BASE_URL:https://api.openai.com/v1}") String baseUrl,
        @Value("${OPENAI_MODEL:gpt-4o-mini}") String model,
        @Value("${OPENAI_TEMPERATURE:}") String temperature,
        @Value("${OPENAI_CACHE_TTL_MS:600000}") long cacheTtlMs,
        @Value("${OPENAI_MIN_INTERVAL_MS:0}") long minIntervalMs,
        @Value("${OPENAI_MAX_RETRIES:2}") int maxRetries,
        @Value("${OPENAI_RETRY_BACKOFF_MS:3000}") long retryBackoffMs
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = (baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl.trim()).replaceAll("/$", "");
        this.model = model == null || model.isBlank() ? "gpt-4o-mini" : model.trim();
        this.temperature = parseTemperature(temperature);
        this.cacheTtlMs = Math.max(0L, cacheTtlMs);
        this.minIntervalMs = Math.max(0L, minIntervalMs);
        this.maxRetries = Math.max(0, maxRetries);
        this.retryBackoffMs = Math.max(0L, retryBackoffMs);

        if (aiEnabled()) {
            log.info("AI enabled. baseUrl={}, model={}, temperature={}, cacheTtlMs={}, minIntervalMs={}, maxRetries={}, apiKey={}",
                this.baseUrl, this.model, this.temperature == null ? "(server default)" : this.temperature,
                this.cacheTtlMs, this.minIntervalMs, this.maxRetries, maskKey(this.apiKey));
        } else {
            log.warn("AI disabled because OPENAI_API_KEY is empty. Fallback templates will be used.");
        }
    }

    /**
     * 解析可选的 temperature：留空或非法值时返回 null（表示不发送该字段）。
     */
    private static Double parseTemperature(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(raw.trim());
        } catch (NumberFormatException exception) {
            log.warn("Invalid OPENAI_TEMPERATURE value '{}', falling back to server default.", raw);
            return null;
        }
    }

    /**
     * 仅在显式配置了 temperature 时才写入请求体，避免部分模型因取值非法而返回 400。
     */
    private void applyTemperature(Map<String, Object> requestBody) {
        if (temperature != null) {
            requestBody.put("temperature", temperature);
        }
    }

    /**
     * 构造请求指纹（kind 区分 json/text，避免不同类型结果互相串用）。
     */
    private String cacheKey(String kind, String systemPrompt, String userPrompt) {
        return kind + "|" + model + "|" + sha256(systemPrompt + "\u0000" + userPrompt);
    }

    private static String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private Object getFromCache(String cacheKey) {
        if (cacheTtlMs <= 0) {
            return null;
        }
        CacheEntry entry = responseCache.get(cacheKey);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtMs() < System.currentTimeMillis()) {
            responseCache.remove(cacheKey);
            return null;
        }
        return entry.value();
    }

    private void putToCache(String cacheKey, Object value) {
        if (cacheTtlMs <= 0) {
            return;
        }
        // 简单容量保护：条目过多时整体清空，避免长时间运行导致内存膨胀
        if (responseCache.size() > 1000) {
            responseCache.clear();
        }
        responseCache.put(cacheKey, new CacheEntry(value, System.currentTimeMillis() + cacheTtlMs));
    }

    private Object lockFor(String cacheKey) {
        return keyLocks.computeIfAbsent(cacheKey, key -> new Object());
    }

    /**
     * 保证两次真实 AI 请求之间间隔不小于 minIntervalMs，以适配低 RPM 配额。
     */
    private void throttle() throws InterruptedException {
        if (minIntervalMs <= 0) {
            return;
        }
        synchronized (throttleLock) {
            long waitMs = lastRequestAtMs + minIntervalMs - System.currentTimeMillis();
            if (waitMs > 0) {
                log.debug("AI throttle: waiting {} ms to respect rate limit", waitMs);
                Thread.sleep(waitMs);
            }
            lastRequestAtMs = System.currentTimeMillis();
        }
    }

    /**
     * 发送请求并返回响应体。遇 429/5xx 自动退避重试，避免前端直接看到"AI 暂时不可用"。
     */
    private String sendWithRetry(String requestJson) throws IOException, InterruptedException {
        for (int attempt = 0; ; attempt++) {
            throttle();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(90))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return response.body();
            }

            boolean retryable = status == 429 || status >= 500;
            if (retryable && attempt < maxRetries) {
                long backoffMs = Math.min(retryBackoffMs * (1L << attempt), 30_000L);
                log.warn("AI request failed with status {} (attempt {}/{}), retrying in {} ms. body={}",
                    status, attempt + 1, maxRetries, backoffMs, truncate(response.body()));
                if (backoffMs > 0) {
                    Thread.sleep(backoffMs);
                }
                continue;
            }

            throw new IllegalStateException("AI request failed with status " + status + ": " + truncate(response.body()));
        }
    }

    /**
     * 统一的"缓存 + 并发合并 + 重试"执行入口。
     * 相同指纹的并发请求只会真正调用一次 AI，其余请求复用结果。
     */
    private Object executeCached(String kind, String systemPrompt, String userPrompt, AiCall call)
            throws IOException, InterruptedException {
        String cacheKey = cacheKey(kind, systemPrompt, userPrompt);

        Object cached = getFromCache(cacheKey);
        if (cached != null) {
            log.debug("AI cache hit ({})", kind);
            return cached;
        }

        synchronized (lockFor(cacheKey)) {
            // 拿到锁后再次检查：并发请求在此期间可能已由其他线程写入缓存
            cached = getFromCache(cacheKey);
            if (cached != null) {
                log.debug("AI cache hit after lock ({})", kind);
                return cached;
            }

            Object value = call.invoke();
            putToCache(cacheKey, value);
            return value;
        }
    }

    /** 供 executeCached 使用的可抛出受检异常的调用接口。 */
    @FunctionalInterface
    private interface AiCall {
        Object invoke() throws IOException, InterruptedException;
    }

    public boolean aiEnabled() {
        return StringUtils.hasText(apiKey);
    }

    /**
     * Call AI with JSON response format.
     */
    public Map<String, Object> chatJson(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        Object result = executeCached("json", systemPrompt, userPrompt, () -> {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            applyTemperature(requestBody);
            requestBody.put("response_format", Map.of("type", "json_object"));
            requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ));

            String responseBody = sendWithRetry(objectMapper.writeValueAsString(requestBody));
            JsonNode payload = objectMapper.readTree(responseBody);
            String content = payload.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("AI response content is empty");
            }

            Map<String, Object> parsed = null;
            try {
                parsed = extractJson(content);
            } catch (Exception e) {
                log.warn("Failed to parse AI JSON response, will attempt heuristics. Error: {}", e.getMessage());
            }

            if (parsed == null) {
                parsed = new LinkedHashMap<>();
            }

            boolean hasStem = StringUtils.hasText(String.valueOf(parsed.getOrDefault("question", parsed.get("stem"))));
            if (!hasStem) {
                log.warn("AI response missing 'question'/'stem'. Raw content: {}", content.length() > 1000 ? content.substring(0, 1000) + "..." : content);
                String heuristic = extractQuestionFromText(content);
                if (StringUtils.hasText(heuristic)) {
                    parsed.put("question", heuristic);
                    parsed.put("stem", heuristic);
                    log.info("Heuristically extracted question/stem from AI response.");
                }
            }

            return parsed;
        });

        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) result;
        // 返回可变副本，避免调用方修改缓存中的对象
        return new LinkedHashMap<>(typed);
    }

    /**
     * Call AI with plain text response format.
     */
    public String chatText(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        Object result = executeCached("text", systemPrompt, userPrompt, () -> {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            applyTemperature(requestBody);
            requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ));

            String responseBody = sendWithRetry(objectMapper.writeValueAsString(requestBody));
            JsonNode payload = objectMapper.readTree(responseBody);
            String content = payload.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("AI response content is empty");
            }
            return content;
        });
        return String.valueOf(result);
    }

    /**
     * Call AI with vision (image) support.
     */
    public String chatWithImage(String systemPrompt, String imageBase64, String imageName) throws IOException, InterruptedException {
        String dataUri = imageBase64.trim();
        if (!dataUri.startsWith("data:")) {
            dataUri = "data:image/png;base64," + dataUri;
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        applyTemperature(requestBody);
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of(
                "role", "user",
                "content", List.of(
                    Map.of("type", "text", "text", "请识别这张学生作答图片中的文字内容。文件名: " + safe(imageName)),
                    Map.of("type", "image_url", "image_url", Map.of("url", dataUri))
                )
            )
        ));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .timeout(Duration.ofSeconds(40))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("chatWithImage failed with status {}: {}", response.statusCode(), truncate(response.body()));
                return "";
            }

            JsonNode payload = objectMapper.readTree(response.body());
            String content = payload.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                return "";
            }
            return String.valueOf(extractJson(content).getOrDefault("text", "")).trim();
        } catch (IOException | InterruptedException exception) {
            // 视觉识别属于可选能力，失败时返回空串由上层降级处理
            log.warn("chatWithImage failed: {}", exception.getMessage());
            return "";
        }
    }

    /**
     * 带图片的 JSON 调用（通用版）。
     *
     * <p>用于让视觉模型阅读图片内容（如扫描版试卷、纯图片资料）并返回结构化 JSON，
     * 是"让 AI 读懂往年题"的基础能力。
     *
     * <p>该请求<b>不参与结果缓存</b>：图片内容各不相同且请求体较大，
     * 缓存收益低而内存开销高；调用方应对识别结果自行落库（见 MaterialTextService）。
     *
     * @param imageBase64   Base64 图片数据，带或不带 data: 前缀均可
     * @param imageMimeType 未带 data: 前缀时使用的 MIME 类型，默认 image/png
     */
    public Map<String, Object> chatJsonWithImage(String systemPrompt, String userPrompt, String imageBase64, String imageMimeType)
            throws IOException, InterruptedException {
        String dataUri = imageBase64 == null ? "" : imageBase64.trim();
        if (!dataUri.startsWith("data:")) {
            String mime = StringUtils.hasText(imageMimeType) ? imageMimeType : "image/png";
            dataUri = "data:" + mime + ";base64," + dataUri;
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        applyTemperature(requestBody);
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", List.of(
                Map.of("type", "text", "text", userPrompt),
                Map.of("type", "image_url", "image_url", Map.of("url", dataUri))
            ))
        ));

        String responseBody = sendWithRetry(objectMapper.writeValueAsString(requestBody));
        JsonNode payload = objectMapper.readTree(responseBody);
        String content = payload.path("choices").path(0).path("message").path("content").asText("");
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("AI vision response content is empty");
        }
        return extractJson(content);
    }

    public Map<String, Object> extractJson(String content) throws IOException {
        String cleaned = content.trim()
            .replaceFirst("^```(?:json)?\\s*", "")
            .replaceFirst("\\s*```$", "");

        try {
            return objectMapper.readValue(cleaned, new TypeReference<>() {});
        } catch (IOException exception) {
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return objectMapper.readValue(cleaned.substring(start, end + 1), new TypeReference<>() {});
            }
            throw exception;
        }
    }

    private String extractQuestionFromText(String text) {
        if (text == null) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\\"question\\\"\\s*:\\s*\\\"([\\s\\S]*?)\\\"");
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        p = java.util.regex.Pattern.compile("(?:题目|题干)[:：\\s]+([\\s\\S]{6,200}?)\\n");
        m = p.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    protected String safe(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 截断错误响应体，避免日志被超长内容淹没。
     */
    private static String truncate(String body) {
        if (body == null) {
            return "(empty)";
        }
        String trimmed = body.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500) + "...";
    }

    private String maskKey(String key) {
        if (!StringUtils.hasText(key)) {
            return "(empty)";
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
    }
}
