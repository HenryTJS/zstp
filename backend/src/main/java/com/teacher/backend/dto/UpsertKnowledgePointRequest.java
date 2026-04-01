package com.teacher.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpsertKnowledgePointRequest(
    String courseName,
    String pointName,
    String parentPoint,
    Integer sortOrder,
    List<Long> prereqIds
) {
}
