package com.teacher.backend.dto;

public record CourseConfigDto(
    String courseName,
    CourseDimensionWeightsDto weights
) {
}

