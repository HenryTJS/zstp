package com.teacher.backend.controller;

import java.util.Map;
import java.util.Objects;

import com.teacher.backend.dto.CourseConfigDto;
import com.teacher.backend.dto.UpsertCourseConfigRequest;
import com.teacher.backend.repository.TeacherCoursePermissionRepository;
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
    private static final String ROLE_TEACHER = "teacher";

    private final UserRepository userRepository;
    private final TeacherCoursePermissionRepository teacherCoursePermissionRepository;
    private final CourseConfigService courseConfigService;

    public CourseConfigController(
        UserRepository userRepository,
        TeacherCoursePermissionRepository teacherCoursePermissionRepository,
        CourseConfigService courseConfigService
    ) {
        this.userRepository = userRepository;
        this.teacherCoursePermissionRepository = teacherCoursePermissionRepository;
        this.courseConfigService = courseConfigService;
    }

    @GetMapping("/{courseName}")
    public ResponseEntity<?> getOne(
        @PathVariable String courseName,
        @RequestParam(required = false) Long adminUserId,
        @RequestParam(required = false) Long teacherUserId
    ) {
        if (!StringUtils.hasText(courseName)) {
            return error(HttpStatus.BAD_REQUEST, "courseName is required");
        }
        if (adminUserId != null) {
            boolean isAdmin = userRepository.findByIdAndRole(adminUserId, ROLE_ADMIN).isPresent();
            if (!isAdmin) {
                return error(HttpStatus.FORBIDDEN, "only admin can read course configs");
            }
        } else if (teacherUserId != null) {
            boolean isTeacher = userRepository.findByIdAndRole(teacherUserId, ROLE_TEACHER).isPresent();
            if (!isTeacher) {
                return error(HttpStatus.FORBIDDEN, "only teacher can read own course configs");
            }
            boolean allowed = teacherCoursePermissionRepository.existsByTeacherIdAndCourseNameIgnoreCase(teacherUserId, courseName);
            if (!allowed) {
                return error(HttpStatus.FORBIDDEN, "teacher has no permission for this course");
            }
        } else {
            return error(HttpStatus.BAD_REQUEST, "adminUserId or teacherUserId is required");
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
        Long teacherUserId = request == null ? null : request.teacherUserId();
        if (adminUserId != null) {
            boolean isAdmin = userRepository.findByIdAndRole(adminUserId, ROLE_ADMIN).isPresent();
            if (!isAdmin) {
                return error(HttpStatus.FORBIDDEN, "only admin can update course configs");
            }
        } else if (teacherUserId != null) {
            boolean isTeacher = userRepository.findByIdAndRole(teacherUserId, ROLE_TEACHER).isPresent();
            if (!isTeacher) {
                return error(HttpStatus.FORBIDDEN, "only teacher can update own course configs");
            }
            boolean allowed = teacherCoursePermissionRepository.existsByTeacherIdAndCourseNameIgnoreCase(teacherUserId, courseName);
            if (!allowed) {
                return error(HttpStatus.FORBIDDEN, "teacher has no permission for this course");
            }
        } else {
            return error(HttpStatus.BAD_REQUEST, "adminUserId or teacherUserId is required");
        }
        try {
            CourseConfigDto saved = courseConfigService.upsertConfig(
                courseName,
                request == null ? null : request.weights()
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

