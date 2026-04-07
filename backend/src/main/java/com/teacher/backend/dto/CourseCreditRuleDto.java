package com.teacher.backend.dto;

import java.util.List;

public record CourseCreditRuleDto(
    double credit,
    List<String> majorCodes
) {
}
