package com.teacher.backend.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.teacher.backend.dto.CourseConfigDto;
import com.teacher.backend.dto.UpsertCourseConfigRequest;
import com.teacher.backend.repository.UserRepository;
import com.teacher.backend.service.CourseConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/course-configs")
public class CourseConfigController {

    private static final String ROLE_ADMIN = "admin";

    private final UserRepository userRepository;
    private final CourseConfigService courseConfigService;

    public CourseConfigController(UserRepository userRepository, CourseConfigService courseConfigService) {
        this.userRepository = userRepository;
        this.courseConfigService = courseConfigService;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) Long adminUserId) {
        if (adminUserId == null) {
            return error(HttpStatus.BAD_REQUEST, "adminUserId is required");
        }
        boolean isAdmin = userRepository.findByIdAndRole(adminUserId, ROLE_ADMIN).isPresent();
        if (!isAdmin) {
            return error(HttpStatus.FORBIDDEN, "only admin can list course configs");
        }
        List<CourseConfigDto> list = courseConfigService.listAllConfigs();
        return ResponseEntity.ok(Map.of("items", list));
    }

    @GetMapping("/{courseName}")
    public ResponseEntity<?> getOne(
        @PathVariable String courseName,
        @RequestParam(required = false) Long adminUserId
    ) {
        if (adminUserId == null) {
            return error(HttpStatus.BAD_REQUEST, "adminUserId is required");
        }
        boolean isAdmin = userRepository.findByIdAndRole(adminUserId, ROLE_ADMIN).isPresent();
        if (!isAdmin) {
            return error(HttpStatus.FORBIDDEN, "only admin can read course configs");
        }
        if (!StringUtils.hasText(courseName)) {
            return error(HttpStatus.BAD_REQUEST, "courseName is required");
        }
        return ResponseEntity.ok(courseConfigService.getOrDefaultConfig(courseName));
    }

    @PutMapping("/{courseName}")
    @Transactional
    public ResponseEntity<?> upsert(@PathVariable String courseName, @RequestBody(required = false) UpsertCourseConfigRequest request) {
        if (!StringUtils.hasText(courseName)) {
            return error(HttpStatus.BAD_REQUEST, "courseName is required");
        }
        Long adminUserId = request == null ? null : request.adminUserId();
        if (adminUserId == null) {
            return error(HttpStatus.BAD_REQUEST, "adminUserId is required");
        }
        boolean isAdmin = userRepository.findByIdAndRole(adminUserId, ROLE_ADMIN).isPresent();
        if (!isAdmin) {
            return error(HttpStatus.FORBIDDEN, "only admin can update course configs");
        }
        try {
            CourseConfigDto saved = courseConfigService.upsertConfig(
                courseName,
                request == null ? null : request.weights(),
                request == null ? null : request.creditRules()
            );
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException ex) {
            return error(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(Objects.requireNonNull(status)).body(Map.of("message", message));
    }
}

