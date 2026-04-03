package com.teacher.backend.dto;

public record SubmitTeacherCoursePermissionRequest(
    Long teacherId,
    String courseName,
    String requestText
) {}

