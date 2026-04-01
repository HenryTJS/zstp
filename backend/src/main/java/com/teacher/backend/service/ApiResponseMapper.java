package com.teacher.backend.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.teacher.backend.entity.Material;
import com.teacher.backend.entity.User;
import org.springframework.stereotype.Component;

@Component
public class ApiResponseMapper {

    public Map<String, Object> toUserMap(User user) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        response.put("role", user.getRole());
        response.put("createdAt", user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
        return response;
    }

    public Map<String, Object> toMaterialMap(Material material) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", material.getId());
        response.put("title", material.getTitle());
        response.put("description", material.getDescription());
        response.put("fileName", material.getFileName());
        response.put("teacherId", material.getTeacher() == null ? null : material.getTeacher().getId());
        response.put("teacherName", material.getTeacher() == null ? "" : material.getTeacher().getUsername());
        response.put("createdAt", material.getCreatedAt() == null ? null : material.getCreatedAt().toString());
        response.put("knowledgePoint", material.getKnowledgePoint());
        return response;
    }
}
