package com.teacher.backend.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.teacher.backend.service.CourseCatalogService;
import com.teacher.backend.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api")
public class CourseController {

    private static final String ROLE_ADMIN = "admin";

    private final CourseCatalogService courseCatalogService;
    private final UserRepository userRepository;

    public CourseController(CourseCatalogService courseCatalogService, UserRepository userRepository) {
        this.courseCatalogService = courseCatalogService;
        this.userRepository = userRepository;
    }

    @GetMapping("/courses")
    public List<String> listCourses(@RequestParam(required = false) String majorCode) {
        // 课程目录：与专业映射暂时不严格绑定，返回全部供前端选择
        return courseCatalogService.allCourses();
    }

    // =========================
    // 管理员课程增删
    // =========================

    @PostMapping("/courses")
    @Transactional
    public ResponseEntity<?> addCourse(@RequestBody(required = false) Map<String, Object> payload) {
        Long adminUserId = payload == null ? null : (payload.get("adminUserId") instanceof Number ? ((Number) payload.get("adminUserId")).longValue() : null);
        String rawName = payload == null ? null : Objects.toString(payload.get("courseName"), null);

        if (adminUserId == null) return error(HttpStatus.BAD_REQUEST, "adminUserId is required");
        if (!StringUtils.hasText(rawName)) return error(HttpStatus.BAD_REQUEST, "courseName is required");

        boolean isAdmin = userRepository.findById(adminUserId)
            .filter(u -> ROLE_ADMIN.equalsIgnoreCase(Objects.toString(u.getRole(), "")))
            .isPresent();
        if (!isAdmin) return error(HttpStatus.FORBIDDEN, "only admin can add courses");

        courseCatalogService.addCourse(rawName);
        return ResponseEntity.ok(Map.of("message", "course added", "courses", courseCatalogService.allCourses()));
    }

    @DeleteMapping("/courses")
    @Transactional
    public ResponseEntity<?> deleteCourse(@RequestParam(required = false) Long adminUserId, @RequestParam String courseName) {
        if (adminUserId == null) return error(HttpStatus.BAD_REQUEST, "adminUserId is required");
        if (!StringUtils.hasText(courseName)) return error(HttpStatus.BAD_REQUEST, "courseName is required");

        boolean isAdmin = userRepository.findById(adminUserId)
            .filter(u -> ROLE_ADMIN.equalsIgnoreCase(Objects.toString(u.getRole(), "")))
            .isPresent();
        if (!isAdmin) return error(HttpStatus.FORBIDDEN, "only admin can delete courses");

        courseCatalogService.deleteCourse(courseName);
        return ResponseEntity.ok(Map.of("message", "course deleted", "courses", courseCatalogService.allCourses()));
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(Objects.requireNonNull(status)).body(Map.of("message", message));
    }
}
