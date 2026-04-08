package com.teacher.backend.controller;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.teacher.backend.service.CourseCatalogService;
import com.teacher.backend.entity.CourseCatalogEntry;
import com.teacher.backend.entity.StudentState;
import com.teacher.backend.entity.User;
import com.teacher.backend.repository.StudentStateRepository;
import com.teacher.backend.repository.TeacherCoursePermissionRepository;
import com.teacher.backend.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final StudentStateRepository studentStateRepository;
    private final TeacherCoursePermissionRepository teacherCoursePermissionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CourseController(CourseCatalogService courseCatalogService, UserRepository userRepository,
                            StudentStateRepository studentStateRepository,
                            TeacherCoursePermissionRepository teacherCoursePermissionRepository) {
        this.courseCatalogService = courseCatalogService;
        this.userRepository = userRepository;
        this.studentStateRepository = studentStateRepository;
        this.teacherCoursePermissionRepository = teacherCoursePermissionRepository;
    }

    @GetMapping("/courses")
    public List<String> listCourses(@RequestParam(required = false) String majorCode) {
        // 课程目录：与专业映射暂时不严格绑定，返回全部供前端选择
        return courseCatalogService.allCourses();
    }

    @GetMapping("/courses/catalog")
    @Transactional(readOnly = true)
    public ResponseEntity<?> listCourseCatalog(@RequestParam(required = false) Long userId) {
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        final Set<String> joined = (user != null && "student".equalsIgnoreCase(safe(user.getRole())))
            ? loadStudentJoinedCourses(user.getId())
            : Set.of();
        final Set<String> authorized = (user != null && "teacher".equalsIgnoreCase(safe(user.getRole())))
            ? teacherCoursePermissionRepository.findByTeacherIdOrderByIdAsc(user.getId()).stream()
                .map(p -> safe(p.getCourseName()))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet())
            : Set.of();
        List<Map<String, Object>> items = courseCatalogService.allCourseEntries().stream().map(e -> {
            Map<String, Object> m = toCatalogMap(e);
            String cn = safe(e.getCourseName());
            boolean hasAccess = false;
            if (user != null) {
                if ("admin".equalsIgnoreCase(safe(user.getRole()))) hasAccess = true;
                else if ("student".equalsIgnoreCase(safe(user.getRole()))) hasAccess = joined.contains(cn);
                else if ("teacher".equalsIgnoreCase(safe(user.getRole()))) hasAccess = authorized.contains(cn);
            }
            m.put("hasAccess", hasAccess);
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("items", items));
    }

    @GetMapping("/courses/detail")
    @Transactional(readOnly = true)
    public ResponseEntity<?> courseDetail(@RequestParam String courseName, @RequestParam(required = false) Long userId) {
        if (!StringUtils.hasText(courseName)) return error(HttpStatus.BAD_REQUEST, "courseName is required");
        CourseCatalogEntry e = courseCatalogService.findEntryByCourseName(courseName).orElse(null);
        if (e == null) return error(HttpStatus.NOT_FOUND, "course not found");
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        boolean hasAccess = false;
        boolean canEditMeta = false;
        if (user != null) {
            String role = safe(user.getRole());
            if ("admin".equalsIgnoreCase(role)) {
                hasAccess = true;
                canEditMeta = true;
            } else if ("teacher".equalsIgnoreCase(role)) {
                hasAccess = teacherCoursePermissionRepository.existsByTeacherIdAndCourseNameIgnoreCase(user.getId(), e.getCourseName());
                canEditMeta = hasAccess;
            } else if ("student".equalsIgnoreCase(role)) {
                hasAccess = loadStudentJoinedCourses(user.getId()).contains(safe(e.getCourseName()));
            }
        }
        Map<String, Object> out = toCatalogMap(e);
        out.put("hasAccess", hasAccess);
        out.put("canEditMeta", canEditMeta);
        return ResponseEntity.ok(out);
    }

    @PutMapping("/courses/meta")
    @Transactional
    public ResponseEntity<?> updateCourseMeta(@RequestBody(required = false) Map<String, Object> payload) {
        Long userId = payload == null ? null : toLong(payload.get("userId"));
        String courseName = payload == null ? null : Objects.toString(payload.get("courseName"), null);
        String coverUrl = payload == null ? null : Objects.toString(payload.get("coverUrl"), null);
        String summary = payload == null ? null : Objects.toString(payload.get("summary"), null);
        String syllabus = payload == null ? null : Objects.toString(payload.get("syllabus"), null);
        if (userId == null) return error(HttpStatus.BAD_REQUEST, "userId is required");
        if (!StringUtils.hasText(courseName)) return error(HttpStatus.BAD_REQUEST, "courseName is required");
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return error(HttpStatus.NOT_FOUND, "user not found");
        String role = safe(user.getRole());
        boolean allow = "admin".equalsIgnoreCase(role) ||
            ("teacher".equalsIgnoreCase(role) && teacherCoursePermissionRepository.existsByTeacherIdAndCourseNameIgnoreCase(userId, courseName));
        if (!allow) return error(HttpStatus.FORBIDDEN, "无权限编辑课程介绍");
        CourseCatalogEntry saved;
        try {
            saved = courseCatalogService.updateCourseMeta(courseName, coverUrl, summary, syllabus, userId);
        } catch (IllegalArgumentException ex) {
            return error(HttpStatus.NOT_FOUND, ex.getMessage());
        }
        return ResponseEntity.ok(toCatalogMap(saved));
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

    private Map<String, Object> toCatalogMap(CourseCatalogEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("courseName", e.getCourseName());
        m.put("sortOrder", e.getSortOrder());
        m.put("coverUrl", StringUtils.hasText(e.getCoverUrl()) ? e.getCoverUrl() : courseCatalogService.defaultCoverUrl(e.getCourseName()));
        m.put("summary", StringUtils.hasText(e.getSummary()) ? e.getSummary() : courseCatalogService.defaultSummary(e.getCourseName()));
        m.put("syllabus", StringUtils.hasText(e.getSyllabus()) ? e.getSyllabus() : courseCatalogService.defaultSyllabus(e.getCourseName()));
        m.put("updatedBy", e.getUpdatedBy());
        m.put("updatedAt", e.getUpdatedAt());
        return m;
    }

    private Set<String> loadStudentJoinedCourses(Long userId) {
        if (userId == null) return Set.of();
        StudentState st = studentStateRepository.findByUserId(userId).orElse(null);
        if (st == null || !StringUtils.hasText(st.getJoinedCoursesJson())) return Set.of();
        try {
            List<String> arr = objectMapper.readValue(st.getJoinedCoursesJson(), new TypeReference<List<String>>() {});
            return arr == null ? Set.of() : arr.stream()
                .map(this::safe)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        } catch (Exception ex) {
            return Set.of();
        }
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        try {
            return v == null ? null : Long.parseLong(String.valueOf(v).trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String safe(String v) {
        return v == null ? "" : v.trim();
    }
}
