package com.teacher.backend.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.teacher.backend.dto.BulkUserImportRequest;
import com.teacher.backend.dto.BulkUserRow;
import com.teacher.backend.dto.ChangePasswordRequest;
import com.teacher.backend.dto.LoginRequest;
import com.teacher.backend.dto.UpdateUserRequest;
import com.teacher.backend.entity.User;
import com.teacher.backend.repository.UserRepository;
import com.teacher.backend.service.ApiResponseMapper;
import com.teacher.backend.service.PasswordService;
import org.springframework.dao.DataIntegrityViolationException;
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
@RequestMapping("/api/users")
public class UserController {

    private static final Set<String> VALID_ROLES = Set.of("student", "teacher");
    private static final String ROLE_ADMIN = "admin";

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final ApiResponseMapper responseMapper;

    public UserController(UserRepository userRepository, PasswordService passwordService, ApiResponseMapper responseMapper) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.responseMapper = responseMapper;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register() {
        return error(HttpStatus.FORBIDDEN, "registration is disabled; please use a preconfigured account");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody(required = false) LoginRequest request) {
        String identity = normalize(request == null ? null : request.identity()).toLowerCase(Locale.ROOT);
        String password = request == null || request.password() == null ? "" : request.password();

        if (!StringUtils.hasText(identity) || !StringUtils.hasText(password)) {
            return error(HttpStatus.BAD_REQUEST, "identity and password are required");
        }

        return userRepository.findByWorkIdIgnoreCaseOrEmailIgnoreCase(identity, identity)
            .filter(user -> passwordService.matches(password, user.getPasswordHash()))
            .<ResponseEntity<?>>map(user -> ResponseEntity.ok(Map.of(
                "message", "ok",
                "user", responseMapper.toUserMap(user)
            )))
            .orElseGet(() -> error(HttpStatus.UNAUTHORIZED, "invalid credentials"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody(required = false) ChangePasswordRequest request) {
        Long userId = request == null ? null : request.userId();
        String currentPassword = request == null || request.currentPassword() == null ? "" : request.currentPassword();
        String newPassword = request == null || request.newPassword() == null ? "" : request.newPassword().trim();

        if (userId == null || !StringUtils.hasText(currentPassword) || newPassword.length() < 6) {
            return error(HttpStatus.BAD_REQUEST, "userId/currentPassword/newPassword(>=6) are required");
        }

        return userRepository.findById(userId)
            .filter(user -> passwordService.matches(currentPassword, user.getPasswordHash()))
            .<ResponseEntity<?>>map(user -> {
                user.setPasswordHash(passwordService.hashPassword(newPassword));
                User savedUser = userRepository.save(user);
                return ResponseEntity.ok(Map.of(
                    "message", "password updated",
                    "user", responseMapper.toUserMap(savedUser)
                ));
            })
            .orElseGet(() -> error(HttpStatus.UNAUTHORIZED, "current password is incorrect"));
    }

    @GetMapping
    public List<Map<String, Object>> listUsers(@RequestParam(required = false) String role) {
        String resolvedRole = normalize(role);
        List<User> users = VALID_ROLES.contains(resolvedRole)
            ? userRepository.findAllByRoleOrderByCreatedAtDesc(resolvedRole)
            : userRepository.findAllByOrderByCreatedAtDesc();
        return users.stream().map(responseMapper::toUserMap).toList();
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(Objects.requireNonNull(status)).body(Map.of("message", message));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateUser(@RequestBody(required = false) UpdateUserRequest request) {
        if (request == null || request.userId() == null) {
            return error(HttpStatus.BAD_REQUEST, "userId is required");
        }

        final Long userId = Objects.requireNonNull(request.userId());

        return userRepository.findById(userId)
            .<ResponseEntity<?>>map(user -> {
                // 明确标注为非空以满足静态空安全检查
                User nonNullUser = Objects.requireNonNull(user);

                String newUsername = request.username() == null ? null : request.username().trim();
                String newEmail = request.email() == null ? null : request.email().trim();
                String newWorkId = request.workId() == null ? null : request.workId().trim();
                if (newUsername != null && newUsername.length() > 0) {
                    nonNullUser.setUsername(newUsername);
                }
                if (newEmail != null && newEmail.length() > 0) {
                    nonNullUser.setEmail(newEmail);
                }
                if (newWorkId != null && newWorkId.length() > 0) {
                    Optional<User> other = userRepository.findByWorkIdIgnoreCase(newWorkId);
                    if (other.isPresent() && !other.get().getId().equals(nonNullUser.getId())) {
                        return error(HttpStatus.BAD_REQUEST, "学工号已被其他账号使用");
                    }
                    nonNullUser.setWorkId(newWorkId);
                }
                try {
                    User saved = userRepository.save(nonNullUser);
                    return ResponseEntity.ok(Map.of("message", "user updated", "user", responseMapper.toUserMap(saved)));
                } catch (DataIntegrityViolationException dive) {
                    return error(HttpStatus.BAD_REQUEST, "用户名或邮箱已存在，请换一个后重试。");
                }
            })
            .orElseGet(() -> error(HttpStatus.NOT_FOUND, "user not found"));
    }

    @PostMapping("/bulk-import")
    @Transactional
    public ResponseEntity<?> bulkImport(@RequestBody(required = false) BulkUserImportRequest request) {
        if (!resolveAdmin(request == null ? null : request.userId()).isPresent()) {
            return error(HttpStatus.FORBIDDEN, "仅管理员可批量导入账号");
        }
        String role = request == null ? "" : normalize(request.role()).toLowerCase(Locale.ROOT);
        if (!VALID_ROLES.contains(role)) {
            return error(HttpStatus.BAD_REQUEST, "role 须为 student 或 teacher");
        }
        List<BulkUserRow> rows = request == null || request.rows() == null ? List.of() : request.rows();
        int created = 0;
        List<Map<String, String>> failures = new ArrayList<>();
        for (BulkUserRow row : rows) {
            if (row == null) {
                continue;
            }
            String username = row.username() == null ? "" : row.username().trim();
            String workId = row.workId() == null ? "" : row.workId().trim();
            if (!StringUtils.hasText(username) || !StringUtils.hasText(workId)) {
                failures.add(failureRow(username, workId, "用户名与学工号均不能为空"));
                continue;
            }
            if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
                failures.add(failureRow(username, workId, "用户名已存在"));
                continue;
            }
            if (userRepository.existsByWorkIdIgnoreCase(workId)) {
                failures.add(failureRow(username, workId, "学工号已存在"));
                continue;
            }
            String email = allocateImportEmail(workId, role);
            User u = new User();
            u.setUsername(username);
            u.setEmail(email);
            u.setWorkId(workId);
            u.setRole(role);
            u.setPasswordHash(passwordService.hashPassword(workId));
            try {
                userRepository.save(u);
                created++;
            } catch (DataIntegrityViolationException ex) {
                failures.add(failureRow(username, workId, "写入失败（可能与其他字段冲突）"));
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "ok");
        body.put("created", created);
        body.put("failures", failures);
        return ResponseEntity.ok(body);
    }

    private static Map<String, String> failureRow(String username, String workId, String reason) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("username", username);
        m.put("workId", workId);
        m.put("reason", reason);
        return m;
    }

    private String allocateImportEmail(String workId, String role) {
        String safe = workId.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!StringUtils.hasText(safe)) {
            safe = "id";
        }
        String prefix = "student".equals(role) ? "s" : "t";
        String local = prefix + safe.toLowerCase(Locale.ROOT);
        String email = local + "@bulk.import.local";
        int i = 0;
        while (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            i++;
            email = local + "_" + i + "@bulk.import.local";
        }
        return email;
    }

    private Optional<User> resolveAdmin(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId).filter(u -> ROLE_ADMIN.equals(u.getRole()));
    }
}
