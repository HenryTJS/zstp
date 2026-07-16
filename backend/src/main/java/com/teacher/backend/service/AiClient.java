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

    public AiClient(
        @Value("${OPENAI_API_KEY:}") String apiKey,
        @Value("${OPENAI_BASE_URL:https://api.openai.com/v1}") String baseUrl,
        @Value("${OPENAI_MODEL:gpt-4o-mini}") String model
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = (baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl.trim()).replaceAll("/$", "");
        this.model = model == null || model.isBlank() ? "gpt-4o-mini" : model.trim();

        if (aiEnabled()) {
            log.info("AI enabled. baseUrl={}, model={}, apiKey={}", this.baseUrl, this.model, maskKey(this.apiKey));
        } else {
            log.warn("AI disabled because OPENAI_API_KEY is empty. Fallback templates will be used.");
        }
    }

    public boolean aiEnabled() {
        return StringUtils.hasText(apiKey);
    }

    /**
     * Call AI with JSON response format.
     */
    public Map<String, Object> chatJson(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.3);
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        ));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .timeout(Duration.ofSeconds(90))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("AI request failed with status " + response.statusCode());
        }

        JsonNode payload = objectMapper.readTree(response.body());
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
    }

    /**
     * Call AI with plain text response format.
     */
    public String chatText(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.5);
        requestBody.put("messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        ));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .timeout(Duration.ofSeconds(90))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("AI request failed with status " + response.statusCode());
        }

        JsonNode payload = objectMapper.readTree(response.body());
        String content = payload.path("choices").path(0).path("message").path("content").asText("");
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("AI response content is empty");
        }
        return content;
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
        requestBody.put("temperature", 0.0);
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

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return "";
        }

        JsonNode payload = objectMapper.readTree(response.body());
        String content = payload.path("choices").path(0).path("message").path("content").asText("");
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return String.valueOf(extractJson(content).getOrDefault("text", "")).trim();
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
