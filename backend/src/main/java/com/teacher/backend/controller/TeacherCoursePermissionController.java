package com.teacher.backend.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.teacher.backend.entity.TeacherCoursePermission;
import com.teacher.backend.repository.TeacherCoursePermissionRepository;
import com.teacher.backend.repository.UserRepository;
import com.teacher.backend.service.CourseCatalogService;

@RestController
@RequestMapping("/api/teacher-course-permissions")
public class TeacherCoursePermissionController {

    private final UserRepository userRepository;
    private final TeacherCoursePermissionRepository permissionRepository;
    private final CourseCatalogService courseCatalogService;

    public TeacherCoursePermissionController(UserRepository userRepository,
                                             TeacherCoursePermissionRepository permissionRepository,
                                             CourseCatalogService courseCatalogService) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.courseCatalogService = courseCatalogService;
    }

    @GetMapping
    public ResponseEntity<?> listTeacherCourses(@RequestParam(required = false) Long teacherId) {
        if (teacherId == null) {
            return error(HttpStatus.BAD_REQUEST, "teacherId is required");
        }
        List<TeacherCoursePermission> rows = permissionRepository.findByTeacherIdOrderByIdAsc(teacherId);
        List<String> courses = rows == null ? Collections.emptyList() : rows.stream()
            .map(TeacherCoursePermission::getCourseName)
            .toList();

        return ResponseEntity.ok(Map.of(
            "teacherId", teacherId,
            "courses", courses
        ));
    }

    /**
     * 学生端课程广场：按课程名批量查询拥有该课程查看权限的教师（展示用户名）。
     */
    @PostMapping("/teachers-for-courses")
    public ResponseEntity<?> listTeachersForCourses(@RequestBody(required = false) Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> rawNames = body == null ? null : (List<String>) body.get("courseNames");
        if (rawNames == null || rawNames.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyMap());
        }
        Set<String> seenCourseKeys = new HashSet<>();
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        for (String rawName : rawNames) {
            if (!StringUtils.hasText(rawName)) {
                continue;
            }
            String course = courseCatalogService.normalizeCourseName(rawName.trim());
            if (!seenCourseKeys.add(course)) {
                continue;
            }
            List<TeacherCoursePermission> rows = permissionRepository.findByCourseNameOrderByTeacherIdAsc(course);
            List<Map<String, Object>> teachers = new ArrayList<>();
            Set<Long> seenTeacherIds = new HashSet<>();
            for (TeacherCoursePermission row : rows) {
                Long tid = row.getTeacherId();
                if (tid == null || !seenTeacherIds.add(tid)) {
                    continue;
                }
                userRepository.findByIdAndRole(tid, "teacher").ifPresent(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("teacherId", u.getId());
                    m.put("username", u.getUsername());
                    teachers.add(m);
                });
            }
            out.put(course, teachers);
        }
        return ResponseEntity.ok(out);
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(Objects.requireNonNull(status)).body(Map.of("message", message));
    }
}

