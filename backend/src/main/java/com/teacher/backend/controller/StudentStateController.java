package com.teacher.backend.controller;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.teacher.backend.repository.StudentStateRepository;
import com.teacher.backend.entity.StudentState;
import com.teacher.backend.entity.User;
import com.teacher.backend.dto.SaveStudentStateRequest;
import com.teacher.backend.repository.UserRepository;
import com.teacher.backend.service.CourseCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Objects;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student-state")
public class StudentStateController {

    private static final String DEFAULT_MAJOR = "计算机";

    private final UserRepository userRepository;
    private final StudentStateRepository studentStateRepository;
    private final CourseCatalogService courseCatalogService;
    private final ObjectMapper objectMapper;

    public StudentStateController(
        UserRepository userRepository,
        StudentStateRepository studentStateRepository,
        CourseCatalogService courseCatalogService,
        ObjectMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.studentStateRepository = studentStateRepository;
        this.courseCatalogService = courseCatalogService;
        this.objectMapper = objectMapper;
    }
    @GetMapping
    public ResponseEntity<?> getState(@RequestParam(required = false) Long userId) {
        if (userId == null) {
            return error(HttpStatus.BAD_REQUEST, "userId is required");
        }

        User user = userRepository.findByIdAndRole(userId, "student").orElse(null);
        if (user == null) {
            return error(HttpStatus.NOT_FOUND, "student user not found");
        }

        StudentState state = studentStateRepository.findByUserId(userId).orElse(null);
        if (state == null) {
            return ResponseEntity.ok(Map.of(
                "userId", userId,
                "major", DEFAULT_MAJOR,
                "majorName", DEFAULT_MAJOR,
                "courseName", courseCatalogService.defaultCourse(),
                "learningRecords", List.of(),
                "wrongBook", List.of(),
                "joinedCourses", List.of(),
                "completedResourceKeys", List.of()
            ));
        }

        // 只返回 code 字段，majorName 与 major 相同（如需展示名称可前端映射）
        String codeRaw = state.getMajor();
        return ResponseEntity.ok(Map.of(
            "userId", userId,
            "major", codeRaw,
            "majorName", codeRaw,
            "courseName", courseCatalogService.normalizeCourseName(state.getCourseName()),
            "learningRecords", parseJsonArray(state.getLearningRecordsJson()),
            "wrongBook", parseJsonArray(state.getWrongBookJson()),
            "joinedCourses", normalizeJoinedCourseNames(parseJsonStringList(state.getJoinedCoursesJson())),
            "completedResourceKeys", parseJsonStringList(state.getCompletedResourceKeysJson())
        ));
    }

    @PostMapping
    public ResponseEntity<?> saveState(@RequestBody(required = false) SaveStudentStateRequest request) {
        Long userId = request == null ? null : request.userId();
        if (userId == null) {
            return error(HttpStatus.BAD_REQUEST, "userId is required");
        }

        User user = userRepository.findByIdAndRole(userId, "student").orElse(null);
        if (user == null) {
            return error(HttpStatus.NOT_FOUND, "student user not found");
        }

        StudentState state = studentStateRepository.findByUserId(userId).orElse(null);
        if (state == null) {
            state = new StudentState();
            state.setUser(user);
        }

        // major 字段只保存 code，直接保存
        String code = request != null ? request.major() : null;
        state.setMajor(code != null ? code : DEFAULT_MAJOR);
        state.setCourseName(courseCatalogService.normalizeCourseName(request != null ? request.courseName() : null));
        state.setLearningRecordsJson(writeJsonArray(request != null ? request.learningRecords() : null));
        state.setWrongBookJson(writeJsonArray(request != null ? request.wrongBook() : null));
        if (request != null && request.joinedCourses() != null) {
            state.setJoinedCoursesJson(writeJsonStringList(normalizeJoinedCourseNames(request.joinedCourses())));
        } else if (state.getId() == null) {
            state.setJoinedCoursesJson("[]");
        }
        if (request != null && request.completedResourceKeys() != null) {
            state.setCompletedResourceKeysJson(writeJsonStringList(request.completedResourceKeys()));
        } else if (state.getId() == null) {
            state.setCompletedResourceKeysJson("[]");
        }
        studentStateRepository.save(state);

        return ResponseEntity.ok(Map.of("message", "saved"));
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(Objects.requireNonNull(status)).body(Map.of("message", message));
    }


    private String writeJsonArray(List<Map<String, Object>> items) {
        try {
            return objectMapper.writeValueAsString(items == null ? List.of() : items);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize student state", exception);
        }
    }

    private List<Map<String, Object>> parseJsonArray(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String writeJsonStringList(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items == null ? List.of() : items);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize joined courses", exception);
        }
    }

    private List<String> parseJsonStringList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(raw, new TypeReference<>() {
            });
            return list == null ? List.of() : list;
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private List<String> normalizeJoinedCourseNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return names.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .map(courseCatalogService::normalizeCourseName)
            .distinct()
            .toList();
    }
}
