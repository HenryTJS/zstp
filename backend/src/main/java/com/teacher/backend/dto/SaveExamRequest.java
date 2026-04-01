package com.teacher.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * Save an exam generated on frontend (batch requests).
 * questions items follow the same schema as /api/generate-question(s):
 * {question, question_type, options, answer, explanation, knowledge_points, ...}
 */
public record SaveExamRequest(String title, Integer durationMinutes, List<Map<String, Object>> questions) {
}

