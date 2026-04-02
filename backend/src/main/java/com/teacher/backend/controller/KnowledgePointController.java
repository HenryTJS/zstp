package com.teacher.backend.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.teacher.backend.dto.UpsertKnowledgePointRequest;
import com.teacher.backend.repository.CourseKnowledgePointPrereqRepository;
import com.teacher.backend.entity.CourseKnowledgePoint;
import com.teacher.backend.repository.CourseKnowledgePointRepository;
import com.teacher.backend.service.CourseCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
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
            // 删除知识点及其前置关系引用
            prereqRepository.deleteByPointId(id);
            prereqRepository.deleteByPrereqPointId(id);
            courseKnowledgePointRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "已删除"));
        } catch (Exception ex) {
            // 返回更友好的错误信息，便于前端诊断
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "删除失败", "error", ex.getMessage()));
        }
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
        String parentPoint = request == null || request.parentPoint() == null ? "" : request.parentPoint().trim();
        Integer sortOrder = request == null || request.sortOrder() == null ? 0 : request.sortOrder();
        java.util.List<Long> prereqIds = request == null || request.prereqIds() == null ? java.util.List.of() : request.prereqIds();
        if (!StringUtils.hasText(pointName)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "pointName is required"));
        }
        if (pointName.equals(parentPoint)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "pointName cannot equal parentPoint"));
        }
        if (isCourseRootPoint(entity)) {
            if (!pointName.equals(entity.getPointName())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "课程根知识点名称不可修改"));
            }
            if (StringUtils.hasText(parentPoint)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "课程根知识点不可设置父节点"));
            }
        }
        entity.setCourseName(courseName);
        entity.setPointName(pointName);
        entity.setParentPoint(StringUtils.hasText(parentPoint) ? parentPoint : null);
        entity.setSortOrder(Math.max(0, sortOrder));
        CourseKnowledgePoint saved = courseKnowledgePointRepository.save(entity);

        // 更新并校验前置关系：只允许与同一父节点下的其它点建立前置
        if (saved.getId() != null) {
            prereqRepository.deleteByPointId(saved.getId());
            for (Long pid : prereqIds) {
                if (pid == null || pid.equals(saved.getId())) continue;
                var opt = courseKnowledgePointRepository.findById(pid);
                if (opt.isPresent()) {
                    var candidate = opt.get();
                    String candidateParent = candidate.getParentPoint();
                    String savedParent = saved.getParentPoint();
                    if ((candidateParent == null && savedParent == null) || (candidateParent != null && candidateParent.equals(savedParent))) {
                        com.teacher.backend.entity.CourseKnowledgePointPrereq pr = new com.teacher.backend.entity.CourseKnowledgePointPrereq();
                        pr.setCourseName(saved.getCourseName());
                        pr.setPointId(saved.getId());
                        pr.setPrereqPointId(pid);
                        prereqRepository.save(pr);
                    }
                }
            }
        }

        return ResponseEntity.ok(Map.of("message", "已更新", "point", toPointMap(saved)));
    }

    private final CourseKnowledgePointRepository courseKnowledgePointRepository;
    private final CourseCatalogService courseCatalogService;
    private final CourseKnowledgePointPrereqRepository prereqRepository;

    public KnowledgePointController(
        CourseKnowledgePointRepository courseKnowledgePointRepository,
        CourseCatalogService courseCatalogService,
        CourseKnowledgePointPrereqRepository prereqRepository
    ) {
        this.courseKnowledgePointRepository = courseKnowledgePointRepository;
        this.courseCatalogService = courseCatalogService;
        this.prereqRepository = prereqRepository;
    }

    @GetMapping
    public Map<String, Object> listByCourse(@RequestParam(required = false) String courseName) {
        String normalizedCourse = courseCatalogService.normalizeCourseName(courseName);
        ensureCourseRootPoint(normalizedCourse);
        List<Map<String, Object>> points = courseKnowledgePointRepository
            .findByCourseNameOrderBySortOrderAscIdAsc(normalizedCourse)
            .stream()
            .map(this::toPointMap)
            .toList();

        return Map.of(
            "courseName", normalizedCourse,
            "courses", courseCatalogService.allCourses(),
            "points", points
        );
    }

    @PostMapping
    public ResponseEntity<?> upsertPoint(@RequestBody(required = false) UpsertKnowledgePointRequest request) {
        String courseName = courseCatalogService.normalizeCourseName(request == null ? null : request.courseName());
        String pointName = request == null || request.pointName() == null ? "" : request.pointName().trim();
        String parentPoint = request == null || request.parentPoint() == null ? "" : request.parentPoint().trim();
        Integer sortOrder = request == null || request.sortOrder() == null ? 0 : request.sortOrder();
        java.util.List<Long> prereqIds = request == null || request.prereqIds() == null ? java.util.List.of() : request.prereqIds();

        if (!StringUtils.hasText(pointName)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "pointName is required"));
        }
        if (pointName.equals(parentPoint)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "pointName cannot equal parentPoint"));
        }
        if (pointName.equals(courseName) && StringUtils.hasText(parentPoint)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "与课程同名的根知识点不可设置父节点"));
        }

        CourseKnowledgePoint entity = courseKnowledgePointRepository
            .findByCourseNameAndPointName(courseName, pointName)
            .orElseGet(CourseKnowledgePoint::new);

        entity.setCourseName(courseName);
        entity.setPointName(pointName);
        entity.setParentPoint(StringUtils.hasText(parentPoint) ? parentPoint : null);
        entity.setSortOrder(Math.max(0, sortOrder));
        CourseKnowledgePoint saved = courseKnowledgePointRepository.save(entity);

        // 保存前置关系（只允许同父节点）
        if (saved.getId() != null) {
            prereqRepository.deleteByPointId(saved.getId());
            for (Long pid : prereqIds) {
                if (pid == null || pid.equals(saved.getId())) continue;
                var opt = courseKnowledgePointRepository.findById(pid);
                if (opt.isPresent()) {
                    var candidate = opt.get();
                    String candidateParent = candidate.getParentPoint();
                    String savedParent = saved.getParentPoint();
                    if ((candidateParent == null && savedParent == null) || (candidateParent != null && candidateParent.equals(savedParent))) {
                        com.teacher.backend.entity.CourseKnowledgePointPrereq pr = new com.teacher.backend.entity.CourseKnowledgePointPrereq();
                        pr.setCourseName(saved.getCourseName());
                        pr.setPointId(saved.getId());
                        pr.setPrereqPointId(pid);
                        prereqRepository.save(pr);
                    }
                }
            }
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
        response.put("parentPoint", point.getParentPoint());
        response.put("sortOrder", point.getSortOrder());
        response.put("createdAt", point.getCreatedAt() == null ? null : point.getCreatedAt().toString());
        // 加入前置 id 列表
        java.util.List<com.teacher.backend.entity.CourseKnowledgePointPrereq> prs = prereqRepository.findByPointId(point.getId());
        java.util.List<Long> prereqIds = prs == null ? java.util.List.of() : prs.stream().map(com.teacher.backend.entity.CourseKnowledgePointPrereq::getPrereqPointId).toList();
        response.put("prereqIds", prereqIds);
        response.put("courseRoot", isCourseRootPoint(point));
        return response;
    }

    private boolean isCourseRootPoint(CourseKnowledgePoint point) {
        if (point == null || point.getPointName() == null || point.getCourseName() == null) {
            return false;
        }
        return point.getPointName().equals(point.getCourseName());
    }

    /** 课程名作为 0 级知识点：与课程同名、无父节点，且不可删除 */
    private void ensureCourseRootPoint(String normalizedCourse) {
        if (!StringUtils.hasText(normalizedCourse)) {
            return;
        }
        if (courseKnowledgePointRepository.findByCourseNameAndPointName(normalizedCourse, normalizedCourse).isEmpty()) {
            CourseKnowledgePoint p = new CourseKnowledgePoint();
            p.setCourseName(normalizedCourse);
            p.setPointName(normalizedCourse);
            p.setParentPoint(null);
            p.setSortOrder(-1000);
            courseKnowledgePointRepository.save(p);
        }
    }
}
