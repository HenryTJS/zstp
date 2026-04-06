package com.teacher.backend.dto;

public record SubmitTeacherCoursePermissionRequest(
    Long teacherId,
    String courseName,
    String requestText,
    /** JOIN_EXISTING（默认）或 CREATE_NEW */
    String requestKind
) {}

