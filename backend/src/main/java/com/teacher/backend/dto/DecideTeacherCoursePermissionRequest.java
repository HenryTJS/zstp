package com.teacher.backend.dto;

public record DecideTeacherCoursePermissionRequest(
    Long adminUserId,
    Long requestId,
    String decision,
    String reason
) {}

