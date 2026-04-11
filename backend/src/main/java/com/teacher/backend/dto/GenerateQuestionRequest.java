package com.teacher.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 命题请求：不依赖学生专业背景，仅按课程主题与知识点出题 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GenerateQuestionRequest(String topic, String difficulty, String questionType) {
}
