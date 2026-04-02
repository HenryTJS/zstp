package com.teacher.backend.dto;

public record CreateAnnouncementRequest(
    String title,
    String content,
    Long userId
) {}
