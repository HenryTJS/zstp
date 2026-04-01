package com.teacher.backend.dto;

import java.util.List;

public record GenerateExamRequest(
    List<String> knowledgePoints,
    Integer choiceCount,
    Integer fillCount,
    Integer essayCount,
    String title,
    Integer durationMinutes
) {
}
