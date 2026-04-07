package com.teacher.backend.dto;

import java.util.List;

public record UpsertCourseConfigRequest(
    Long adminUserId,
    String courseName,
    CourseDimensionWeightsDto weights,
    List<CourseCreditRuleDto> creditRules
) {
}

