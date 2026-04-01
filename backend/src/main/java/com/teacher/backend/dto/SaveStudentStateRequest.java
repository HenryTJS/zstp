package com.teacher.backend.dto;

import java.util.List;
import java.util.Map;

public record SaveStudentStateRequest(
    Long userId,
    String major,
    String courseName,
    List<Map<String, Object>> learningRecords,
    List<Map<String, Object>> wrongBook
) {
}
