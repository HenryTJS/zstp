package com.teacher.backend.dto;

public record CreateKnowledgePointDiscussionRequest(
    Long userId,
    String courseName,
    String pointName,
    String content,
    Long parentId,
    String postKind
) {}
