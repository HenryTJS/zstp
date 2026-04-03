package com.teacher.backend.dto;

import java.util.List;

public record AssignTeacherCoursesRequest(
    Long adminUserId,
    Long teacherId,
    List<String> courseNames
) {}

