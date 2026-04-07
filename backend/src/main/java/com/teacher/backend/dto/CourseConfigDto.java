package com.teacher.backend.dto;

import java.util.List;

public record CourseConfigDto(
    String courseName,
    CourseDimensionWeightsDto weights,
    List<CourseCreditRuleDto> creditRules
) {
}

