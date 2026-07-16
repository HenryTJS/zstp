package com.teacher.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 命题请求：按课程主题与知识点出题，可选传入知识点描述以提升出题质量 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GenerateQuestionRequest(
    String topic,
    String difficulty,
    String questionType,
    /** 知识点描述/定义文本，传入后 AI 可基于此出更准确的题目 */
    String knowledgePointDescription
) {
}
