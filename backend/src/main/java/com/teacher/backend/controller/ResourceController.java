package com.teacher.backend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teacher.backend.entity.CourseKnowledgePoint;
import com.teacher.backend.entity.KnowledgePointPublishedTest;
import com.teacher.backend.entity.StudentState;
import com.teacher.backend.entity.User;
import com.teacher.backend.repository.CourseKnowledgePointRepository;
import com.teacher.backend.repository.KnowledgePointPublishedTestRepository;
import com.teacher.backend.repository.MaterialRepository;
import com.teacher.backend.repository.StudentStateRepository;
import com.teacher.backend.repository.TeacherCoursePermissionRepository;
import com.teacher.backend.repository.UserRepository;
import com.teacher.backend.service.ApiResponseMapper;
import com.teacher.backend.service.CourseCatalogService;
import com.teacher.backend.util.KnowledgePointUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final MaterialRepository materialRepository;
    private final KnowledgePointPublishedTestRepository testRepository;
    private final CourseKnowledgePointRepository courseKnowledgePointRepository;
    private final TeacherCoursePermissionRepository teacherCoursePermissionRepository;
    private final StudentStateRepository studentStateRepository;
    private final UserRepository userRepository;
    private final CourseCatalogService courseCatalogService;
    private final ApiResponseMapper responseMapper;
    private final ObjectMapper objectMapper;

    public ResourceController(
            MaterialRepository materialRepository,
            KnowledgePointPublishedTestRepository testRepository,
            CourseKnowledgePointRepository courseKnowledgePointRepository,
            TeacherCoursePermissionRepository teacherCoursePermissionRepository,
            StudentStateRepository studentStateRepository,
            UserRepository userRepository,
            CourseCatalogService courseCatalogService,
            ApiResponseMapper responseMapper,
            ObjectMapper objectMapper
    ) {
        this.materialRepository = materialRepository;
        this.testRepository = testRepository;
        this.courseKnowledgePointRepository = courseKnowledgePointRepository;
        this.teacherCoursePermissionRepository = teacherCoursePermissionRepository;
        this.studentStateRepository = studentStateRepository;
        this.userRepository = userRepository;
        this.courseCatalogService = courseCatalogService;
        this.responseMapper = responseMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 聚合资源：材料（视频/文档/附件） + 教师发布测试元数据（若存在）。
     * - student 访问：传 userId（校验加入课程）
     * - teacher 访问：传 teacherId（校验课程权限）
     */
    @GetMapping("/by-knowledge-point")
    @Transactional(readOnly = true)
    public ResponseEntity<?> byKnowledgePoint(
            @RequestParam String courseName,
            @RequestParam String knowledgePoint,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "true") boolean includeAncestors
    ) {
        String cn = courseCatalogService.normalizeCourseName(courseName);
        String kp = knowledgePoint == null ? "" : String.valueOf(knowledgePoint).trim();
        if (!StringUtils.hasText(cn) || !StringUtils.hasText(kp)) {
            return error(HttpStatus.BAD_REQUEST, "courseName 与 knowledgePoint 不能为空");
        }

        if (teacherId != null) {
            boolean allowed = teacherCoursePermissionRepository.existsByTeacherIdAndCourseName(teacherId, cn);
            if (!allowed) {
                return ResponseEntity.ok(Map.of("materials", List.of(), "tests", List.of(), "totalCount", 0));
            }
        }

        if (userId != null) {
            User st = userRepository.findById(userId).orElse(null);
            if (st == null || !"student".equals(st.getRole())) {
                return error(HttpStatus.FORBIDDEN, "仅学生可查看");
            }
            if (!studentJoinedCourse(userId, cn)) {
                return error(HttpStatus.FORBIDDEN, "未加入该课程");
            }
        }

        List<CourseKnowledgePoint> allPoints = courseKnowledgePointRepository.findByCourseNameOrderBySortOrderAscIdAsc(cn);
        var descendants = KnowledgePointUtils.getAllDescendants(kp, allPoints);
        var ancestors = KnowledgePointUtils.getAllAncestors(kp, allPoints);
        List<String> queryPoints = new java.util.ArrayList<>(includeAncestors ? ancestors : List.of());
        queryPoints.addAll(descendants);
        queryPoints = queryPoints.stream().distinct().toList();

        List<Map<String, Object>> materials = materialRepository
                .findByCourseNameAndKnowledgePointIn(cn, queryPoints)
                .stream()
                .map(responseMapper::toMaterialMap)
                .toList();

        List<Map<String, Object>> tests = buildTestMetaList(cn, kp);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("materials", materials);
        out.put("tests", tests);
        out.put("totalCount", materials.size() + tests.size());
        return ResponseEntity.ok(out);
    }

    @PostMapping("/complete")
    @Transactional
    public ResponseEntity<?> markComplete(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) return error(HttpStatus.BAD_REQUEST, "body 必填");
        Long userId = toLong(body.get("userId"));
        String courseName = courseCatalogService.normalizeCourseName(String.valueOf(body.getOrDefault("courseName", "")));
        String resourceKey = String.valueOf(body.getOrDefault("resourceKey", "")).trim();
        if (userId == null) return error(HttpStatus.BAD_REQUEST, "userId 必填");
        if (!StringUtils.hasText(courseName)) return error(HttpStatus.BAD_REQUEST, "courseName 必填");
        if (!StringUtils.hasText(resourceKey)) return error(HttpStatus.BAD_REQUEST, "resourceKey 必填");

        User st = userRepository.findById(userId).orElse(null);
        if (st == null || !"student".equals(st.getRole())) return error(HttpStatus.FORBIDDEN, "仅学生可操作");
        if (!studentJoinedCourse(userId, courseName)) return error(HttpStatus.FORBIDDEN, "未加入该课程");

        StudentState state = studentStateRepository.findByUserId(userId).orElse(null);
        if (state == null) {
            return error(HttpStatus.BAD_REQUEST, "学生状态未初始化，请先进入学生端保存一次学习状态");
        }

        Set<String> keys = new HashSet<>(parseJsonStringList(state.getCompletedResourceKeysJson()));
        keys.add(resourceKey);
        state.setCompletedResourceKeysJson(writeJsonStringList(keys.stream().toList()));
        studentStateRepository.save(state);
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    @GetMapping("/progress")
    @Transactional(readOnly = true)
    public ResponseEntity<?> progress(@RequestParam Long userId, @RequestParam String courseName) {
        if (userId == null) return error(HttpStatus.BAD_REQUEST, "userId 必填");
        String cn = courseCatalogService.normalizeCourseName(courseName);
        if (!StringUtils.hasText(cn)) return error(HttpStatus.BAD_REQUEST, "courseName 必填");
        User st = userRepository.findById(userId).orElse(null);
        if (st == null || !"student".equals(st.getRole())) return error(HttpStatus.FORBIDDEN, "仅学生可查看");
        if (!studentJoinedCourse(userId, cn)) return error(HttpStatus.FORBIDDEN, "未加入该课程");

        StudentState state = studentStateRepository.findByUserId(userId).orElse(null);
        List<String> completed = state == null ? List.of() : parseJsonStringList(state.getCompletedResourceKeysJson());

        // 与教师端总进度保持同一口径：统一使用课程资源键集合计数
        Set<String> validKeys = buildCourseResourceKeys(cn);
        int total = validKeys.size();

        int completedCount = 0;
        for (String k : completed) {
            if (validKeys.contains(k)) completedCount++;
        }
        int percent = total <= 0 ? 0 : (int) Math.round((completedCount * 100.0) / total);

        return ResponseEntity.ok(Map.of(
                "courseName", cn,
                "total", total,
                "completed", completedCount,
                "percent", percent,
                "completedKeys", completed
        ));
    }

    /**
     * 教师端：课程总进度（按学生降序）
     */
    @GetMapping("/course-progress-overview")
    @Transactional(readOnly = true)
    public ResponseEntity<?> courseProgressOverview(@RequestParam Long teacherUserId, @RequestParam String courseName) {
        if (teacherUserId == null) return error(HttpStatus.BAD_REQUEST, "teacherUserId 必填");
        String cn = courseCatalogService.normalizeCourseName(courseName);
        if (!StringUtils.hasText(cn)) return error(HttpStatus.BAD_REQUEST, "courseName 必填");

        User teacher = userRepository.findById(teacherUserId).orElse(null);
        if (teacher == null || (!"teacher".equals(teacher.getRole()) && !"admin".equals(teacher.getRole()))) {
            return error(HttpStatus.FORBIDDEN, "仅教师或管理员可查看");
        }
        if (!"admin".equals(teacher.getRole())
                && !teacherCoursePermissionRepository.existsByTeacherIdAndCourseName(teacherUserId, cn)) {
            return error(HttpStatus.FORBIDDEN, "无该课程权限");
        }

        Set<String> validKeys = buildCourseResourceKeys(cn);
        int total = validKeys.size();

        List<Long> userIds = studentStateRepository.findUserIdsWithCourseInJoined(cn);
        List<User> users = userRepository.findAllById(Objects.requireNonNull(userIds)).stream()
                .filter(u -> "student".equals(u.getRole()))
                .toList();

        List<Map<String, Object>> students = users.stream().map(u -> {
            StudentState state = studentStateRepository.findByUserId(u.getId()).orElse(null);
            List<String> completed = state == null ? List.of() : parseJsonStringList(state.getCompletedResourceKeysJson());
            int completedCount = 0;
            for (String k : completed) {
                if (validKeys.contains(k)) completedCount++;
            }
            int percent = total <= 0 ? 0 : (int) Math.round((completedCount * 100.0) / total);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", u.getId());
            row.put("workId", u.getWorkId());
            row.put("username", u.getUsername());
            row.put("completed", completedCount);
            row.put("total", total);
            row.put("percent", percent);
            return row;
        }).sorted((a, b) -> Integer.compare(toInt(b.get("percent")), toInt(a.get("percent")))).toList();

        return ResponseEntity.ok(Map.of(
                "courseName", cn,
                "totalResourceCount", total,
                "studentCount", students.size(),
                "students", students
        ));
    }

    private List<Map<String, Object>> buildTestMetaList(String courseName, String pointName) {
        Optional<KnowledgePointPublishedTest> opt = testRepository.findByCourseNameAndPointName(courseName, pointName);
        if (opt.isEmpty()) return List.of();
        KnowledgePointPublishedTest t = opt.get();
        int cnt = 0;
        try {
            List<Map<String, Object>> qs = objectMapper.readValue(
                    t.getQuestionsJson() == null ? "[]" : t.getQuestionsJson(),
                    new TypeReference<>() {});
            cnt = qs == null ? 0 : qs.size();
        } catch (Exception ignored) {
            cnt = 0;
        }
        if (cnt <= 0) return List.of();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("courseName", t.getCourseName());
        m.put("pointName", t.getPointName());
        m.put("title", t.getTitle());
        m.put("updatedAt", t.getUpdatedAt() == null ? null : t.getUpdatedAt().toString());
        m.put("questionCount", cnt);
        // 用于前端统一“资源项”标识
        m.put("resourceKey", "TEST:" + t.getId());
        return List.of(m);
    }

    private Set<String> buildCourseResourceKeys(String courseName) {
        String cn = courseCatalogService.normalizeCourseName(courseName);
        Set<String> validKeys = new HashSet<>();
        materialRepository.findAll().stream()
                .filter(m -> cn.equals(courseCatalogService.normalizeCourseName(m.getCourseName())))
                .forEach(m -> validKeys.add("MATERIAL:" + m.getId()));
        testRepository.findByCourseName(cn).stream()
                .filter(t -> questionCountOf(t) > 0)
                .forEach(t -> validKeys.add("TEST:" + t.getId()));
        return validKeys;
    }

    private int questionCountOf(KnowledgePointPublishedTest t) {
        if (t == null) return 0;
        try {
            List<Map<String, Object>> qs = objectMapper.readValue(
                    t.getQuestionsJson() == null ? "[]" : t.getQuestionsJson(),
                    new TypeReference<>() {});
            return qs == null ? 0 : qs.size();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private boolean studentJoinedCourse(Long userId, String courseName) {
        Optional<StudentState> opt = studentStateRepository.findByUserId(userId);
        if (opt.isEmpty()) return false;
        String raw = opt.get().getJoinedCoursesJson();
        if (!StringUtils.hasText(raw)) return false;
        try {
            List<String> list = objectMapper.readValue(raw, new TypeReference<>() {});
            if (list == null) return false;
            String norm = courseCatalogService.normalizeCourseName(courseName);
            for (String c : list) {
                if (norm.equals(courseCatalogService.normalizeCourseName(c))) return true;
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private List<String> parseJsonStringList(String raw) {
        if (!StringUtils.hasText(raw)) return List.of();
        try {
            List<String> list = objectMapper.readValue(raw, new TypeReference<>() {});
            return list == null ? List.of() : list;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String writeJsonStringList(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items == null ? List.of() : items);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize completed keys", ex);
        }
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(o).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(Objects.requireNonNull(status)).body(Map.of("message", message));
    }
}

