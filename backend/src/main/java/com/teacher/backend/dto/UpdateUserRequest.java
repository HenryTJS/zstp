package com.teacher.backend.dto;

public record UpdateUserRequest(Long userId, String username, String email) {
}
