package com.teacher.backend.controller;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

import com.teacher.backend.service.CourseCatalogService;
import com.teacher.backend.entity.CourseCatalogEntry;
import com.teacher.backend.entity.StudentState;
import com.teacher.backend.entity.User;
import com.teacher.backend.repository.StudentStateRepository;
import com.teacher.backend.repository.TeacherCoursePermissionRepository;
import com.teacher.backend.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api")
public class CourseController {

    private final CourseCatalogService courseCatalogService;
    private final UserRepository userRepository;
    private final StudentStateRepository studentStateRepository;
    private final TeacherCoursePermissionRepository teacherCoursePermissionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path uploadRoot;

    public CourseController(CourseCatalogService courseCatalogService, UserRepository userRepository,
                            StudentStateRepository studentStateRepository,
                            TeacherCoursePermissionRepository teacherCoursePermissionRepository,
                            @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.courseCatalogService = courseCatalogService;
        this.userRepository = userRepository;
        this.studentStateRepository = studentStateRepository;
        this.teacherCoursePermissionRepository = teacherCoursePermissionRepository;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
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
        Map<String, Object> out = toCatalogMap(e);
        putDetailAccessFlags(out, user, e);
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
        boolean allow = hasCourseMetaEditPermission(user, courseName);
        if (!allow) return error(HttpStatus.FORBIDDEN, "无权限编辑课程介绍");
        CourseCatalogEntry saved;
        try {
            saved = courseCatalogService.updateCourseMeta(courseName, coverUrl, summary, syllabus, userId);
        } catch (IllegalArgumentException ex) {
            return error(HttpStatus.NOT_FOUND, ex.getMessage());
        }
        Map<String, Object> out = toCatalogMap(saved);
        putDetailAccessFlags(out, user, saved);
        return ResponseEntity.ok(out);
    }

    @PostMapping("/courses/cover/upload")
    @Transactional
    public ResponseEntity<?> uploadCourseCover(
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String courseName,
        @RequestParam(required = false) MultipartFile file
    ) {
        if (userId == null) return error(HttpStatus.BAD_REQUEST, "userId is required");
        if (!StringUtils.hasText(courseName)) return error(HttpStatus.BAD_REQUEST, "courseName is required");
        if (file == null || file.isEmpty()) return error(HttpStatus.BAD_REQUEST, "file is required");
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return error(HttpStatus.NOT_FOUND, "user not found");
        if (!hasCourseMetaEditPermission(user, courseName)) return error(HttpStatus.FORBIDDEN, "无权限上传课程封面");

        String original = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), ""));
        String lower = original.toLowerCase();
        String format = null;
        if (lower.endsWith(".png")) format = "png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) format = "jpg";
        if (format == null) return error(HttpStatus.BAD_REQUEST, "仅支持 png/jpg/jpeg");

        try {
            Path dir = uploadRoot.resolve("course-covers").normalize();
            Files.createDirectories(dir);
            String slug = safe(courseName).replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}_-]+", "_");
            if (!StringUtils.hasText(slug)) slug = "course";
            String storedName = slug + "_" + Instant.now().toEpochMilli() + "." + format;
            Path target = dir.resolve(storedName).normalize();
            if (!target.startsWith(dir)) return error(HttpStatus.BAD_REQUEST, "invalid filename");
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            if (!Files.exists(target) || !Files.isRegularFile(target) || Files.size(target) <= 0) {
                try { Files.deleteIfExists(target); } catch (Exception ignore) {}
                return error(HttpStatus.INTERNAL_SERVER_ERROR, "封面写入失败（服务器无写入权限或磁盘异常）");
            }
            String url = "/api/courses/cover/" + storedName;
            return ResponseEntity.ok(Map.of("coverUrl", url));
        } catch (Exception ex) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "封面上传失败: " + ex.getMessage());
        }
    }

    @GetMapping("/courses/cover/{filename:.+}")
    public ResponseEntity<?> getCourseCover(@PathVariable String filename) {
        try {
            Path dir = uploadRoot.resolve("course-covers").normalize();
            Path p = dir.resolve(filename).normalize();
            if (!p.startsWith(dir)) return error(HttpStatus.BAD_REQUEST, "非法路径");
            if (!Files.exists(p) || !Files.isRegularFile(p)) return error(HttpStatus.NOT_FOUND, "封面不存在");
            Resource res = new UrlResource(p.toUri());
            String lower = p.getFileName().toString().toLowerCase();
            MediaType type = lower.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
            return ResponseEntity.ok().contentType(type).body(res);
        } catch (Exception ex) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "读取封面失败");
        }
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(Objects.requireNonNull(status)).body(Map.of("message", message));
    }

    /**
     * 与 {@link #courseDetail} 一致：详情/保存课程元数据后前端依赖 hasAccess、canEditMeta 控制按钮。
     */
    private void putDetailAccessFlags(Map<String, Object> out, User user, CourseCatalogEntry e) {
        boolean hasAccess = false;
        boolean canEditMeta = false;
        if (user != null && e != null) {
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
        out.put("hasAccess", hasAccess);
        out.put("canEditMeta", canEditMeta);
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

    private boolean hasCourseMetaEditPermission(User user, String courseName) {
        if (user == null) return false;
        String role = safe(user.getRole());
        if ("admin".equalsIgnoreCase(role)) return true;
        return "teacher".equalsIgnoreCase(role)
            && teacherCoursePermissionRepository.existsByTeacherIdAndCourseNameIgnoreCase(user.getId(), courseName);
    }

}
