package com.teacher.backend.dto;

public record GenerateQuestionRequest(String topic, String difficulty, String questionType, String major) {
}
