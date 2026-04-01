package com.teacher.backend.dto;

public record ChangePasswordRequest(Long userId, String currentPassword, String newPassword) {
}