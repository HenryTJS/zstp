package com.teacher.backend.dto;

import java.util.List;
import java.util.Map;

public record StudentDimensionScoresResponse(
    Long userId,
    String majorCode,
    String courseNameScope,
    Map<String, Double> dimensionScores,
    Map<String, Double> dimensionWeights,
    List<Map<String, Object>> usedCourses
) {
}

