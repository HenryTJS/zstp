package com.teacher.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpsertKnowledgePointRequest(
    String courseName,
    String pointName,
    Long parentId,
    String parentPoint,
    Integer sortOrder
) {
}
