package com.teacher.backend.dto;

public record UpsertCourseConfigRequest(
    Long adminUserId,
    Long teacherUserId,
    String courseName,
    CourseDimensionWeightsDto weights
) {
}

