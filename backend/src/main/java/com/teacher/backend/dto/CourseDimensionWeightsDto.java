package com.teacher.backend.dto;

public record CourseDimensionWeightsDto(
    double logicReasoning,
    double numericCalculation,
    double semanticUnderstanding,
    double spatialImagination,
    double memoryRetrieval
) {
}

