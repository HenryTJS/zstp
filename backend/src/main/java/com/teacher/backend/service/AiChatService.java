package com.teacher.backend.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Service for AI-powered chat/agent interactions.
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private final AiClient aiClient;

    public AiChatService(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    /**
     * Agent-style chat with AI for answering student questions.
     */
    public Map<String, Object> agentChat(String question, String role, String username) {
        String q = safe(question);
        String r = safe(role);
        String u = safe(username);

        if (!StringUtils.hasText(q)) {
            return Map.of("answer", "请先输入你的问题。");
        }

        if (!aiClient.aiEnabled()) {
            return Map.of("answer", "AI 助手暂未配置（OPENAI_API_KEY 为空），无法回答你的问题。请先联系管理员配置后再试。");
        }

        String systemPrompt = "你是智能学习助手，名叫「智学小T」。"
            + "你正在帮助一位" + (StringUtils.hasText(r) ? r : "学生") + "解答学习问题。"
            + (StringUtils.hasText(u) ? "对方用户名：" + u + "。" : "")
            + "请用简体中文、友好且专业的语气回答。"
            + "如果问题涉及具体学科知识，请给出清晰的定义、步骤或示例。"
            + "如果问题不明确，可以追问澄清。"
            + "输出 JSON:{answer:string}。";

        String userPrompt = q;

        try {
            Map<String, Object> result = aiClient.chatJson(systemPrompt, userPrompt);
            String answer = String.valueOf(result.getOrDefault("answer", ""));
            if (!StringUtils.hasText(answer)) {
                answer = "抱歉，我暂时无法回答这个问题，请换个方式再问一次。";
            }
            return Map.of("answer", answer);
        } catch (Exception exception) {
            log.warn("agentChat failed: {}", exception.getMessage());
            return Map.of("answer", "AI 服务暂时不可用，请稍后重试。");
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
