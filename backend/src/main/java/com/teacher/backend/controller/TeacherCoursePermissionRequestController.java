package com.teacher.backend.controller;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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

import com.teacher.backend.dto.DecideTeacherCoursePermissionRequest;
import com.teacher.backend.dto.SubmitTeacherCoursePermissionRequest;
import com.teacher.backend.entity.TeacherCoursePermission;
import com.teacher.backend.entity.TeacherCoursePermissionRequest;
import com.teacher.backend.entity.User;
import com.teacher.backend.repository.TeacherCoursePermissionRepository;
import com.teacher.backend.repository.TeacherCoursePermissionRequestRepository;
import com.teacher.backend.repository.UserRepository;
import com.teacher.backend.service.CourseCatalogService;

@RestController
@RequestMapping("/api/teacher-course-permission-requests")
public class TeacherCoursePermissionRequestController {
    private static final String ROLE_ADMIN = "admin";

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    private static final String DECISION_APPROVE = "approve";
    private static final String DECISION_REJECT = "reject";

    private final UserRepository userRepository;
    private final TeacherCoursePermissionRepository permissionRepository;
    private final TeacherCoursePermissionRequestRepository requestRepository;
    private final CourseCatalogService courseCatalogService;

    public TeacherCoursePermissionRequestController(UserRepository userRepository,
                                                    TeacherCoursePermissionRepository permissionRepository,
                                                    TeacherCoursePermissionRequestRepository requestRepository,
                                                    CourseCatalogService courseCatalogService) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.requestRepository = requestRepository;
        this.courseCatalogService = courseCatalogService;
    }

    @GetMapping
    public ResponseEntity<?> list(
        @RequestParam(required = false) Long teacherId,
        @RequestParam(required = false) Long adminUserId,
        @RequestParam(required = false) String status
    ) {
        if (teacherId != null) {
            List<TeacherCoursePermissionRequest> rows = requestRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId);
            String normalizedStatus = normalizeStatus(status);
            if (normalizedStatus != null) {
                rows = rows.stream().filter(r -> normalizedStatus.equals(r.getStatus())).toList();
            }
            return ResponseEntity.ok(toDtoList(rows, false));
        }

        if (adminUserId == null) {
            return error(HttpStatus.BAD_REQUEST, "teacherId or adminUserId is required");
        }

        boolean isAdmin = userRepository.findByIdAndRole(adminUserId, ROLE_ADMIN).isPresent();
        if (!isAdmin) {
            return error(HttpStatus.FORBIDDEN, "only admin can list requests");
        }

        String normalizedStatus = normalizeStatus(status);
        List<TeacherCoursePermissionRequest> rows;
        if (normalizedStatus != null) {
            rows = requestRepository.findByStatusOrderByCreatedAtDesc(normalizedStatus);
        } else {
            rows = requestRepository.findAll();
            rows.sort((a, b) -> {
                LocalDateTime at = a.getCreatedAt();
                LocalDateTime bt = b.getCreatedAt();
                if (at == null && bt == null) return 0;
                if (at == null) return 1;
                if (bt == null) return -1;
                return bt.compareTo(at);
            });
        }

        return ResponseEntity.ok(toDtoList(rows, true));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> submit(@RequestBody(required = false) SubmitTeacherCoursePermissionRequest request) {
        if (request == null) {
            return error(HttpStatus.BAD_REQUEST, "request body is required");
        }
        Long teacherId = request.teacherId();
        String rawCourseName = request.courseName();
        String requestText = request.requestText();

        if (teacherId == null) return error(HttpStatus.BAD_REQUEST, "teacherId is required");
        if (!StringUtils.hasText(rawCourseName)) return error(HttpStatus.BAD_REQUEST, "courseName is required");
        if (!StringUtils.hasText(requestText)) return error(HttpStatus.BAD_REQUEST, "requestText is required");

        boolean teacherExists = userRepository.findByIdAndRole(teacherId, "teacher").isPresent();
        if (!teacherExists) return error(HttpStatus.NOT_FOUND, "teacher not found");

        String normalizedCourseName = courseCatalogService.normalizeCourseName(rawCourseName);

        boolean alreadyHasPermission = permissionRepository.existsByTeacherIdAndCourseName(teacherId, normalizedCourseName);
        if (alreadyHasPermission) {
            return error(HttpStatus.CONFLICT, "teacher already has permission for this course");
        }

        Optional<TeacherCoursePermissionRequest> pendingOpt = requestRepository.findFirstByTeacherIdAndCourseNameAndStatusOrderByCreatedAtDesc(
            teacherId,
            normalizedCourseName,
            STATUS_PENDING
        );
        if (pendingOpt.isPresent()) {
            return error(HttpStatus.CONFLICT, "there is already a pending request for this course");
        }

        TeacherCoursePermissionRequest row = new TeacherCoursePermissionRequest();
        row.setTeacherId(teacherId);
        row.setCourseName(normalizedCourseName);
        row.setRequestText(requestText.trim());
        row.setStatus(STATUS_PENDING);
        row.setAdminUserId(null);
        row.setAdminReason(null);
        row.setDecidedAt(null);
        requestRepository.save(row);

        return ResponseEntity.ok(Map.of(
            "message", "submitted",
            "requestId", row.getId(),
            "teacherId", teacherId,
            "courseName", normalizedCourseName,
            "status", row.getStatus()
        ));
    }

    @PostMapping("/decide")
    @Transactional
    public ResponseEntity<?> decide(@RequestBody(required = false) DecideTeacherCoursePermissionRequest request) {
        if (request == null) {
            return error(HttpStatus.BAD_REQUEST, "request body is required");
        }

        Long adminUserId = request.adminUserId();
        Long requestId = request.requestId();
        String decision = request.decision();
        String reason = request.reason();

        if (adminUserId == null) return error(HttpStatus.BAD_REQUEST, "adminUserId is required");
        if (requestId == null) return error(HttpStatus.BAD_REQUEST, "requestId is required");
        if (!StringUtils.hasText(decision)) return error(HttpStatus.BAD_REQUEST, "decision is required");
        if (!StringUtils.hasText(reason)) return error(HttpStatus.BAD_REQUEST, "reason is required");

        boolean isAdmin = userRepository.findByIdAndRole(adminUserId, ROLE_ADMIN).isPresent();
        if (!isAdmin) return error(HttpStatus.FORBIDDEN, "only admin can decide");

        TeacherCoursePermissionRequest row = requestRepository.findById(requestId).orElse(null);
        if (row == null) return error(HttpStatus.NOT_FOUND, "request not found");
        if (!STATUS_PENDING.equals(row.getStatus())) {
            return error(HttpStatus.CONFLICT, "request already decided");
        }

        String normalizedDecision = decision.trim().toLowerCase();
        String nextStatus;
        if (DECISION_APPROVE.equals(normalizedDecision)) {
            nextStatus = STATUS_APPROVED;
        } else if (DECISION_REJECT.equals(normalizedDecision)) {
            nextStatus = STATUS_REJECTED;
        } else {
            return error(HttpStatus.BAD_REQUEST, "decision must be approve or reject");
        }

        row.setStatus(nextStatus);
        row.setAdminUserId(adminUserId);
        row.setAdminReason(reason.trim());
        row.setDecidedAt(LocalDateTime.now());

        if (STATUS_APPROVED.equals(nextStatus)) {
            // 若已存在则不重复写（幂等）
            boolean exists = permissionRepository.existsByTeacherIdAndCourseName(row.getTeacherId(), row.getCourseName());
            if (!exists) {
                TeacherCoursePermission p = new TeacherCoursePermission();
                p.setTeacherId(row.getTeacherId());
                p.setCourseName(row.getCourseName());
                permissionRepository.save(p);
            }
        }

        requestRepository.save(row);

        return ResponseEntity.ok(Map.of(
            "message", "decided",
            "requestId", requestId,
            "status", row.getStatus()
        ));
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) return null;
        String s = status.trim().toUpperCase();
        if (STATUS_PENDING.equals(s)) return STATUS_PENDING;
        if (STATUS_APPROVED.equals(s)) return STATUS_APPROVED;
        if (STATUS_REJECTED.equals(s)) return STATUS_REJECTED;
        return null;
    }

    private List<Map<String, Object>> toDtoList(List<TeacherCoursePermissionRequest> rows, boolean includeTeacher) {
        if (rows == null || rows.isEmpty()) return Collections.emptyList();

        return rows.stream().map(r -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("teacherId", r.getTeacherId());
            m.put("courseName", r.getCourseName());
            m.put("requestText", r.getRequestText());
            m.put("status", r.getStatus());
            m.put("createdAt", r.getCreatedAt());
            m.put("adminUserId", r.getAdminUserId());
            m.put("adminReason", r.getAdminReason());
            m.put("decidedAt", r.getDecidedAt());

            if (includeTeacher) {
                User u = null;
                Long tid = r.getTeacherId();
                if (tid != null) {
                    u = userRepository.findById(tid).orElse(null);
                }
                m.put("teacherUsername", u == null ? null : u.getUsername());
            }
            return m;
        }).toList();
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(Objects.requireNonNull(status)).body(Map.of("message", message));
    }
}

