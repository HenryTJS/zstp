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
import com.teacher.backend.entity.UserNotification;
import com.teacher.backend.repository.TeacherCoursePermissionRepository;
import com.teacher.backend.repository.TeacherCoursePermissionRequestRepository;
import com.teacher.backend.repository.UserRepository;
import com.teacher.backend.repository.UserNotificationRepository;
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

    /** 申请已有目录中的课程权限 */
    private static final String KIND_JOIN_EXISTING = "JOIN_EXISTING";
    /** 申请新增一门课程（通过后写入目录并授权） */
    private static final String KIND_CREATE_NEW = "CREATE_NEW";

    /** 与前端 DiscussionNotificationBell 约定：教师端权限审批结果通知 */
    private static final String NOTIFY_TYPE_TEACHER_PERMISSION = "TEACHER_PERMISSION";

    private final UserRepository userRepository;
    private final TeacherCoursePermissionRepository permissionRepository;
    private final TeacherCoursePermissionRequestRepository requestRepository;
    private final CourseCatalogService courseCatalogService;
    private final UserNotificationRepository userNotificationRepository;

    public TeacherCoursePermissionRequestController(UserRepository userRepository,
                                                    TeacherCoursePermissionRepository permissionRepository,
                                                    TeacherCoursePermissionRequestRepository requestRepository,
                                                    CourseCatalogService courseCatalogService,
                                                    UserNotificationRepository userNotificationRepository) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.requestRepository = requestRepository;
        this.courseCatalogService = courseCatalogService;
        this.userNotificationRepository = userNotificationRepository;
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

        String rawKind = request.requestKind();
        String requestKind = KIND_JOIN_EXISTING;
        if (StringUtils.hasText(rawKind)) {
            String k = rawKind.trim().toUpperCase();
            if (KIND_CREATE_NEW.equals(k)) {
                requestKind = KIND_CREATE_NEW;
            } else if (KIND_JOIN_EXISTING.equals(k)) {
                requestKind = KIND_JOIN_EXISTING;
            } else {
                return error(HttpStatus.BAD_REQUEST, "requestKind must be JOIN_EXISTING or CREATE_NEW");
            }
        }

        if (KIND_JOIN_EXISTING.equals(requestKind)) {
            if (!courseCatalogService.isCourseInCatalog(normalizedCourseName)) {
                return error(HttpStatus.BAD_REQUEST, "该课程不在课程目录中；若需新增课程，请使用「申请新课程」并选择对应类型。");
            }
        } else {
            if (courseCatalogService.isCourseInCatalog(normalizedCourseName)) {
                return error(HttpStatus.CONFLICT, "该课程已在目录中，请在课程广场找到该课程后使用「加入课程」申请权限。");
            }
        }

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
        row.setRequestKind(requestKind);
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
            // 必须与申请/权限表中的 course_name 一致写入目录（不能用 addCourse+normalize，子串/别名会把新课名归并到旧课导致不插入）
            courseCatalogService.ensureCatalogContainsExactCourseName(row.getCourseName());
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

        notifyTeacherPermissionDecision(row);

        return ResponseEntity.ok(Map.of(
            "message", "decided",
            "requestId", requestId,
            "status", row.getStatus()
        ));
    }

    /**
     * 审批通过/拒绝后推送到教师「通知」列表（与公告、讨论区通知同一套 UI）。
     */
    private void notifyTeacherPermissionDecision(TeacherCoursePermissionRequest row) {
        Long teacherId = row.getTeacherId();
        if (teacherId == null) {
            return;
        }
        boolean approved = STATUS_APPROVED.equals(row.getStatus());
        String course = row.getCourseName() == null ? "" : row.getCourseName().trim();
        String kindLabel = KIND_CREATE_NEW.equals(row.getRequestKind()) ? "新开课程" : "加入已有课程";
        String reason = row.getAdminReason() == null ? "" : row.getAdminReason().trim();

        String title = approved ? "课程权限申请已通过" : "课程权限申请未通过";
        StringBuilder body = new StringBuilder();
        if (approved) {
            body.append("管理员已通过你的申请（").append(kindLabel).append("）。");
        } else {
            body.append("管理员未通过你的申请（").append(kindLabel).append("）。");
        }
        if (StringUtils.hasText(course)) {
            body.append(" 课程：").append(course).append("。");
        }
        if (StringUtils.hasText(reason)) {
            body.append(" 说明：").append(reason);
        }
        String bodyStr = body.toString();
        if (bodyStr.length() > 500) {
            bodyStr = bodyStr.substring(0, 497) + "...";
        }

        UserNotification n = new UserNotification();
        n.setUserId(teacherId);
        n.setType(NOTIFY_TYPE_TEACHER_PERMISSION);
        n.setTitle(title);
        n.setBody(bodyStr);
        n.setRead(false);
        n.setCourseName(course);
        n.setPointName("");
        n.setPostId(row.getId() == null ? 0L : row.getId());
        userNotificationRepository.save(n);
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
            String rk = r.getRequestKind();
            m.put("requestKind", (rk == null || rk.isBlank()) ? KIND_JOIN_EXISTING : rk);
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

