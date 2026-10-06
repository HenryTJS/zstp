package com.teacher.backend.dto;

/**
 * AI 问答请求。
 *
 * <p>courseName + knowledgePoint 用于检索该知识点下的课程资料，
 * 使 AI 能基于课件内容作答并给出参考出处；两者为空时退化为通用问答。
 */
public record AgentChatRequest(
    String question,
    String role,
    String userId,
    String username,
    String courseName,
    String knowledgePoint
) {
}
