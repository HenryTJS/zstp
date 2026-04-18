package com.teacher.backend.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.teacher.backend.dto.UpsertKnowledgePointRequest;
import com.teacher.backend.entity.CourseKnowledgePoint;
import com.teacher.backend.repository.CourseKnowledgePointRepository;
import com.teacher.backend.repository.TeacherCoursePermissionRepository;
import com.teacher.backend.service.CourseCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-points")
public class KnowledgePointController {
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deletePoint(@PathVariable long id) {
        if (!courseKnowledgePointRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "知识点不存在"));
        }
        var toDelete = courseKnowledgePointRepository.findById(id).orElse(null);
        if (toDelete != null && isCourseRootPoint(toDelete)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "课程根知识点不可删除"));
        }
        try {
            Set<Long> idsToDelete = collectSubtreeIds(toDelete);
            courseKnowledgePointRepository.deleteAllById(idsToDelete);
            return ResponseEntity.ok(Map.of("message", "已删除", "deletedCount", idsToDelete.size()));
        } catch (Exception ex) {
            // 返回更友好的错误信息，便于前端诊断
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "删除失败", "error", ex.getMessage()));
        }
    }

    /** 删除某节点时，连同所有下级知识点一起删除。 */
    private Set<Long> collectSubtreeIds(CourseKnowledgePoint root) {
        Set<Long> ids = new HashSet<>();
        if (root == null || root.getId() == null) return ids;

        List<CourseKnowledgePoint> all = courseKnowledgePointRepository.findByCourseNameOrderBySortOrderAscIdAsc(root.getCourseName());
        Map<Long, List<CourseKnowledgePoint>> childrenByParentId = new HashMap<>();
        for (CourseKnowledgePoint point : all) {
            Long pid = point.getParentId();
            if (pid != null) {
                childrenByParentId.computeIfAbsent(pid, k -> new ArrayList<>()).add(point);
            }
        }

        ArrayDeque<CourseKnowledgePoint> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            CourseKnowledgePoint current = queue.removeFirst();
            if (current.getId() == null || !ids.add(current.getId())) continue;
            for (CourseKnowledgePoint child : childrenByParentId.getOrDefault(current.getId(), List.of())) {
                queue.addLast(child);
            }
        }
        return ids;
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePoint(@PathVariable long id, @RequestBody UpsertKnowledgePointRequest request) {
        var entityOpt = courseKnowledgePointRepository.findById(id);
        if (entityOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "知识点不存在"));
        }
        var entity = entityOpt.get();
        String courseName = courseCatalogService.normalizeCourseName(request == null ? null : request.courseName());
        String pointName = request == null || request.pointName() == null ? "" : request.pointName().trim();
        Long parentId = request == null ? null : request.parentId();
        String parentPoint = request == null || request.parentPoint() == null ? "" : request.parentPoint().trim();
        Integer sortOrder = request == null || request.sortOrder() == null ? 0 : request.sortOrder();
        if (!StringUtils.hasText(pointName)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "pointName is required"));
        }
        if (parentId != null && entity.getId() != null && parentId.equals(entity.getId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "point cannot parent itself"));
        }
        CourseKnowledgePoint parentEntity = null;
        if (parentId != null) {
            parentEntity = courseKnowledgePointRepository.findById(parentId).orElse(null);
            if (parentEntity == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "parentId not found"));
            }
            if (!courseName.equals(parentEntity.getCourseName())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "parentId must be in same course"));
            }
        } else if (StringUtils.hasText(parentPoint)) {
            // 兼容旧请求：未传 parentId 时，按名称回退（重名时取第一条）
            parentEntity = courseKnowledgePointRepository.findByCourseNameAndPointName(courseName, parentPoint).orElse(null);
        }
        if (isCourseRootPoint(entity)) {
            if (!pointName.equals(entity.getPointName())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "课程根知识点名称不可修改"));
            }
            if (parentEntity != null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "课程根知识点不可设置父节点"));
            }
        }
        entity.setCourseName(courseName);
        entity.setPointName(pointName);
        entity.setParentId(parentEntity == null ? null : parentEntity.getId());
        entity.setParentPoint(parentEntity == null ? null : parentEntity.getPointName());
        entity.setSortOrder(Math.max(0, sortOrder));
        CourseKnowledgePoint saved = courseKnowledgePointRepository.save(entity);

        return ResponseEntity.ok(Map.of("message", "已更新", "point", toPointMap(saved)));
    }

    private final CourseKnowledgePointRepository courseKnowledgePointRepository;
    private final CourseCatalogService courseCatalogService;
    private final TeacherCoursePermissionRepository teacherCoursePermissionRepository;

    public KnowledgePointController(
        CourseKnowledgePointRepository courseKnowledgePointRepository,
        CourseCatalogService courseCatalogService,
        TeacherCoursePermissionRepository teacherCoursePermissionRepository
    ) {
        this.courseKnowledgePointRepository = courseKnowledgePointRepository;
        this.courseCatalogService = courseCatalogService;
        this.teacherCoursePermissionRepository = teacherCoursePermissionRepository;
    }

    @GetMapping
    public Map<String, Object> listByCourse(
        @RequestParam(required = false) String courseName,
        @RequestParam(required = false) Long teacherId
    ) {
        String normalizedCourse = courseCatalogService.normalizeCourseName(courseName);

        // 当传入 teacherId 时，只允许访问管理员配置的课程
        if (teacherId != null) {
            boolean allowed = teacherCoursePermissionRepository.existsByTeacherIdAndCourseName(teacherId, normalizedCourse);
            if (!allowed) {
                return Map.of(
                    "courseName", normalizedCourse,
                    "courses", List.of(),
                    "points", List.of()
                );
            }
        }

        ensureCourseRootPoint(normalizedCourse);
        List<Map<String, Object>> points = courseKnowledgePointRepository
            .findByCourseNameOrderBySortOrderAscIdAsc(normalizedCourse)
            .stream()
            .map(this::toPointMap)
            .toList();

        List<String> courses;
        if (teacherId != null) {
            courses = teacherCoursePermissionRepository.findByTeacherIdOrderByIdAsc(teacherId).stream()
                .map(com.teacher.backend.entity.TeacherCoursePermission::getCourseName)
                .distinct()
                .toList();
        } else {
            courses = courseCatalogService.allCourses();
        }

        return Map.of(
            "courseName", normalizedCourse,
            "courses", courses,
            "points", points
        );
    }

    @PostMapping
    public ResponseEntity<?> upsertPoint(@RequestBody(required = false) UpsertKnowledgePointRequest request) {
        String courseName = courseCatalogService.normalizeCourseName(request == null ? null : request.courseName());
        String pointName = request == null || request.pointName() == null ? "" : request.pointName().trim();
        Long parentId = request == null ? null : request.parentId();
        String parentPoint = request == null || request.parentPoint() == null ? "" : request.parentPoint().trim();
        Integer sortOrder = request == null || request.sortOrder() == null ? 0 : request.sortOrder();

        if (!StringUtils.hasText(pointName)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "pointName is required"));
        }
        CourseKnowledgePoint parentEntity = null;
        if (parentId != null) {
            parentEntity = courseKnowledgePointRepository.findById(parentId).orElse(null);
            if (parentEntity == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "parentId not found"));
            }
            if (!courseName.equals(parentEntity.getCourseName())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "parentId must be in same course"));
            }
        } else if (StringUtils.hasText(parentPoint)) {
            // 兼容旧请求：未传 parentId 时，按名称回退（重名时取第一条）
            parentEntity = courseKnowledgePointRepository.findByCourseNameAndPointName(courseName, parentPoint).orElse(null);
        }

        if (pointName.equals(courseName) && parentEntity != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "与课程同名的根知识点不可设置父节点"));
        }

        Long parentValueId = parentEntity == null ? null : parentEntity.getId();
        String parentValue = parentEntity == null ? null : parentEntity.getPointName();
        CourseKnowledgePoint entity = courseKnowledgePointRepository
            .findByCourseNameAndPointNameAndParentId(courseName, pointName, parentValueId)
            .orElseGet(CourseKnowledgePoint::new);

        entity.setCourseName(courseName);
        entity.setPointName(pointName);
        entity.setParentId(parentValueId);
        entity.setParentPoint(parentValue);
        entity.setSortOrder(Math.max(0, sortOrder));
        CourseKnowledgePoint saved;
        try {
            saved = courseKnowledgePointRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            // 让前端导入时拿到更可读的失败原因（通常是唯一约束仍未按“允许重名”更新）
            String detail = ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage();
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", "知识点保存失败（可能仍存在旧唯一约束冲突）。",
                "error", detail
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "message", "知识点保存失败。",
                "error", ex.getMessage()
            ));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "message", "saved",
            "point", toPointMap(saved)
        ));
    }

    private Map<String, Object> toPointMap(CourseKnowledgePoint point) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", point.getId());
        response.put("courseName", point.getCourseName());
        response.put("pointName", point.getPointName());
        response.put("parentId", point.getParentId());
        response.put("parentPoint", point.getParentPoint());
        response.put("sortOrder", point.getSortOrder());
        response.put("createdAt", point.getCreatedAt() == null ? null : point.getCreatedAt().toString());
        response.put("courseRoot", isCourseRootPoint(point));
        return response;
    }

    private boolean isCourseRootPoint(CourseKnowledgePoint point) {
        if (point == null || point.getPointName() == null || point.getCourseName() == null) {
            return false;
        }
        // 允许重名后，单纯 pointName == courseName 不再足够判断“课程根”。
        // 课程根应当是：名称与课程同名，且父节点为空。
        return point.getPointName().equals(point.getCourseName()) && point.getParentId() == null;
    }

    /** 课程名作为 0 级知识点：与课程同名、无父节点，且不可删除 */
    private void ensureCourseRootPoint(String normalizedCourse) {
        if (!StringUtils.hasText(normalizedCourse)) {
            return;
        }
        if (courseKnowledgePointRepository.findByCourseNameAndPointNameAndParentId(normalizedCourse, normalizedCourse, null).isEmpty()) {
            CourseKnowledgePoint p = new CourseKnowledgePoint();
            p.setCourseName(normalizedCourse);
            p.setPointName(normalizedCourse);
            p.setParentId(null);
            p.setParentPoint(null);
            p.setSortOrder(-1000);
            courseKnowledgePointRepository.save(p);
        }
    }
}
