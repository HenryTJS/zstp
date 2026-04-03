package com.teacher.backend.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.teacher.backend.entity.Material;
import com.teacher.backend.entity.User;
import com.teacher.backend.repository.StudentStateRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ApiResponseMapper {

    private final StudentStateRepository studentStateRepository;
    private final MajorPathLookupService majorPathLookupService;

    public ApiResponseMapper(StudentStateRepository studentStateRepository, MajorPathLookupService majorPathLookupService) {
        this.studentStateRepository = studentStateRepository;
        this.majorPathLookupService = majorPathLookupService;
    }

    public Map<String, Object> toUserMap(User user) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        response.put("workId", user.getWorkId());
        response.put("role", user.getRole());
        response.put("college", user.getCollege());
        response.put("createdAt", user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
        if ("student".equals(user.getRole())) {
            enrichStudentMajorLevels(response, user.getId());
        }
        return response;
    }

    private void enrichStudentMajorLevels(Map<String, Object> response, Long userId) {
        if (userId == null) {
            return;
        }
        studentStateRepository.findByUserId(userId).ifPresentOrElse(state -> {
            String code = state.getMajor();
            List<String> names = majorPathLookupService.resolvePathNames(code);
            if (!names.isEmpty()) {
                response.put("major1", names.get(0));
                response.put("major2", names.size() > 1 ? names.get(1) : null);
                response.put("major3", names.size() > 2 ? names.get(2) : null);
            } else if (StringUtils.hasText(code)) {
                response.put("major1", null);
                response.put("major2", null);
                response.put("major3", code);
            } else {
                response.put("major1", null);
                response.put("major2", null);
                response.put("major3", null);
            }
        }, () -> {
            // 尚未产生 student_states 记录（学生从未触发过保存学习状态）
            response.put("major1", "未同步");
            response.put("major2", null);
            response.put("major3", null);
        });
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
