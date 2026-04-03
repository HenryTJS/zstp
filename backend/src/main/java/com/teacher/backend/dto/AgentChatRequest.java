package com.teacher.backend.dto;

public record AgentChatRequest(String question, String role, String userId, String username) {
}
