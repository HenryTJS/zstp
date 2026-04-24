package com.teacher.backend.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.teacher.backend.entity.CourseKnowledgePoint;
import com.teacher.backend.repository.CourseKnowledgePointRepository;
import com.teacher.backend.util.KnowledgePointUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.teacher.backend.entity.Material;
import com.teacher.backend.entity.MaterialCategory;
import com.teacher.backend.entity.User;
import com.teacher.backend.repository.MaterialRepository;
import com.teacher.backend.repository.UserRepository;
import com.teacher.backend.repository.TeacherCoursePermissionRepository;
import com.teacher.backend.service.ApiResponseMapper;
import com.teacher.backend.service.CourseCatalogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/materials")
public class MaterialController {

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMaterial(@PathVariable long id) {
        if (!materialRepository.existsById(id)) {
            return error(HttpStatus.NOT_FOUND, "资料不存在");
        }
        materialRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "已删除"));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> download(@PathVariable long id) {
        Material m = materialRepository.findById(id).orElse(null);
        if (m == null) {
            return error(HttpStatus.NOT_FOUND, "资料不存在");
        }
        if (!StringUtils.hasText(m.getFilePath())) {
            return error(HttpStatus.NOT_FOUND, "该资料无可下载文件");
        }

        try {
            Path p = Paths.get(m.getFilePath()).toAbsolutePath().normalize();
            if (!p.startsWith(uploadRoot)) {
                // 防止任意文件读取
                return error(HttpStatus.BAD_REQUEST, "非法文件路径");
            }
            if (!Files.exists(p) || !Files.isRegularFile(p)) {
                return error(HttpStatus.NOT_FOUND, "文件不存在");
            }

            Resource resource = new UrlResource(Objects.requireNonNull(p.toUri()));
            if (!resource.exists()) {
                return error(HttpStatus.NOT_FOUND, "文件不存在");
            }

            String filename = StringUtils.hasText(m.getFileName()) ? m.getFileName().trim() : p.getFileName().toString();
            MediaType contentType;
            try {
                String probed = Files.probeContentType(p);
                contentType = StringUtils.hasText(probed)
                    ? MediaType.parseMediaType(Objects.requireNonNull(probed))
                    : MediaType.APPLICATION_OCTET_STREAM;
            } catch (Exception ex) {
                contentType = MediaType.APPLICATION_OCTET_STREAM;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
            return new ResponseEntity<>(resource, headers, HttpStatus.OK);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "下载失败", "error", ex.getMessage()));
        }
    }

    private final UserRepository userRepository;
    private final MaterialRepository materialRepository;
    private final ApiResponseMapper responseMapper;
    private final TeacherCoursePermissionRepository teacherCoursePermissionRepository;
    private final CourseCatalogService courseCatalogService;
    private final Path uploadRoot;
    private final CourseKnowledgePointRepository courseKnowledgePointRepository;

    public MaterialController(
        UserRepository userRepository,
        MaterialRepository materialRepository,
        ApiResponseMapper responseMapper,
        TeacherCoursePermissionRepository teacherCoursePermissionRepository,
        CourseCatalogService courseCatalogService,
        CourseKnowledgePointRepository courseKnowledgePointRepository,
        @Value("${app.upload-dir:uploads}") String uploadDir
    ) {
        this.userRepository = userRepository;
        this.materialRepository = materialRepository;
        this.responseMapper = responseMapper;
        this.teacherCoursePermissionRepository = teacherCoursePermissionRepository;
        this.courseCatalogService = courseCatalogService;
        this.courseKnowledgePointRepository = courseKnowledgePointRepository;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }
    /**
     * 查询某知识点及其所有下级的资料（层级继承显示）
     * @param knowledgePoint 知识点名
     * @param includeAncestors 是否包含所有上级（true=上层也能看到下层资料，false=只下级）
     */
    @GetMapping("/by-knowledge-point")
    public List<Map<String, Object>> listByKnowledgePoint(
        @RequestParam String courseName,
        @RequestParam String knowledgePoint,
        @RequestParam(required = false) Long teacherId,
        @RequestParam(defaultValue = "true") boolean includeAncestors
    ) {
        String normalizedCourse = courseCatalogService.normalizeCourseName(courseName);
        if (teacherId != null) {
            boolean allowed = teacherCoursePermissionRepository.existsByTeacherIdAndCourseName(teacherId, normalizedCourse);
            if (!allowed) {
                return List.of();
            }
        }
        List<CourseKnowledgePoint> allPoints = courseKnowledgePointRepository.findByCourseNameOrderBySortOrderAscIdAsc(normalizedCourse);
        // 获取所有下级
        var descendants = KnowledgePointUtils.getAllDescendants(knowledgePoint, allPoints);
        // 获取所有上级
        var ancestors = KnowledgePointUtils.getAllAncestors(knowledgePoint, allPoints);
        // 需要查的知识点集合（使用可变列表以便后续添加）
        List<String> queryPoints = new java.util.ArrayList<>(includeAncestors ? ancestors : java.util.List.of());
        queryPoints.addAll(descendants);
        // 去重
        queryPoints = queryPoints.stream().distinct().toList();
        return materialRepository.findByCourseNameAndKnowledgePointIn(normalizedCourse, queryPoints).stream()
            .map(responseMapper::toMaterialMap)
            .toList();
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
        @RequestParam(required = false) String title,
        @RequestParam(required = false) String description,
        @RequestParam(required = false) String teacherId,
        @RequestParam(required = false) String courseName,
        @RequestParam(required = false) String knowledgePoint,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) MultipartFile file
    ) {
        String resolvedTitle = title == null ? "" : title.trim();
        String resolvedDescription = description == null ? "" : description.trim();
        String resolvedCourseName = courseCatalogService.normalizeCourseName(courseName);
        String resolvedKnowledgePoint = knowledgePoint == null ? "" : knowledgePoint.trim();
        if (!StringUtils.hasText(resolvedTitle)) {
            return error(HttpStatus.BAD_REQUEST, "title is required");
        }
        if (!StringUtils.hasText(teacherId)) {
            return error(HttpStatus.BAD_REQUEST, "teacherId is required");
        }
        if (!StringUtils.hasText(resolvedCourseName)) {
            return error(HttpStatus.BAD_REQUEST, "courseName is required");
        }
        if (!StringUtils.hasText(resolvedKnowledgePoint)) {
            return error(HttpStatus.BAD_REQUEST, "knowledgePoint is required");
        }
        if (!StringUtils.hasText(category)) {
            return error(HttpStatus.BAD_REQUEST, "category is required");
        }

        MaterialCategory cat;
        try {
            cat = MaterialCategory.valueOf(category.trim().toUpperCase());
        } catch (Exception ex) {
            return error(HttpStatus.BAD_REQUEST, "invalid category");
        }
        if (file == null || file.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "file is required");
        }

        Long teacherIdValue;
        try {
            teacherIdValue = Long.parseLong(teacherId.trim());
        } catch (NumberFormatException exception) {
            return error(HttpStatus.BAD_REQUEST, "teacherId must be an integer");
        }

        User teacher = userRepository.findByIdAndRole(teacherIdValue, "teacher")
            .orElse(null);
        if (teacher == null) {
            return error(HttpStatus.NOT_FOUND, "teacher not found");
        }

        // 校验该教师对课程有权限
        boolean allowed = teacherCoursePermissionRepository.existsByTeacherIdAndCourseName(teacher.getId(), resolvedCourseName);
        if (!allowed) {
            return error(HttpStatus.FORBIDDEN, "无该课程权限");
        }

        String fileName = null;
        String filePath = null;
        String contentType = null;
        Long sizeBytes = null;
        if (file != null && !file.isEmpty()) {
            String originalFileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename(), ""));
            if (StringUtils.hasText(originalFileName)) {
                String lower = originalFileName.toLowerCase();
                if (cat == MaterialCategory.VIDEO && !lower.endsWith(".mp4")) {
                    return error(HttpStatus.BAD_REQUEST, "VIDEO 仅支持 .mp4");
                }
                if (cat == MaterialCategory.DOCUMENT && !lower.endsWith(".pdf")) {
                    return error(HttpStatus.BAD_REQUEST, "DOCUMENT 仅支持 .pdf");
                }
                String finalName = teacher.getId() + "_" + originalFileName.replace("..", "");
                Path destination = uploadRoot.resolve(finalName).normalize();
                if (!destination.startsWith(uploadRoot)) {
                    return error(HttpStatus.BAD_REQUEST, "invalid file name");
                }
                try {
                    Files.createDirectories(uploadRoot);
                    Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed to save uploaded file", exception);
                }
                fileName = originalFileName;
                filePath = destination.toString();
                contentType = file.getContentType();
                try {
                    sizeBytes = file.getSize();
                } catch (Exception ignored) {
                    sizeBytes = null;
                }
            }
        }

        Material material = new Material();

        material.setTitle(resolvedTitle);
        material.setDescription(resolvedDescription);
        material.setFileName(fileName);
        material.setFilePath(filePath);
        material.setCourseName(resolvedCourseName);
        material.setKnowledgePoint(resolvedKnowledgePoint);
        material.setCategory(cat);
        material.setContentType(contentType);
        material.setSizeBytes(sizeBytes);
        material.setTeacher(teacher);
        material = materialRepository.save(material);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "message", "uploaded",
            "material", responseMapper.toMaterialMap(material)
        ));
    }

    @GetMapping
    public List<Map<String, Object>> listMaterials() {
        return materialRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(responseMapper::toMaterialMap)
            .toList();
    }

    

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(Objects.requireNonNull(status)).body(Map.of("message", message));
    }
}
