package com.teacher.backend.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.teacher.backend.entity.UserNotification;
import com.teacher.backend.repository.UserNotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class UserNotificationController {

    private final UserNotificationRepository notificationRepository;

    public UserNotificationController(UserNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam Long userId, @RequestParam(defaultValue = "50") int limit) {
        if (userId == null) {
            return error(HttpStatus.BAD_REQUEST, "userId 必填");
        }
        int cap = Math.min(Math.max(limit, 1), 100);
        Pageable page = PageRequest.of(0, cap);
        List<UserNotification> list = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, page);
        long unread = notificationRepository.countByUserIdAndReadIsFalse(userId);
        List<Map<String, Object>> items = list.stream().map(this::toDto).toList();
        return ResponseEntity.ok(Map.of("items", items, "unreadCount", unread));
    }

    private Map<String, Object> toDto(UserNotification n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("type", n.getType());
        m.put("title", n.getTitle());
        m.put("body", n.getBody());
        m.put("read", n.isRead());
        m.put("courseName", n.getCourseName());
        m.put("pointName", n.getPointName());
        m.put("postId", n.getPostId());
        m.put("createdAt", n.getCreatedAt() == null ? null : n.getCreatedAt().toString());
        return m;
    }

    @PostMapping("/{id}/read")
    @Transactional
    public ResponseEntity<?> markRead(@PathVariable Long id, @RequestParam Long userId) {
        if (userId == null) {
            return error(HttpStatus.BAD_REQUEST, "userId 必填");
        }
        int updated = notificationRepository.markRead(id, userId);
        if (updated == 0) {
            return error(HttpStatus.NOT_FOUND, "通知不存在");
        }
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(Objects.requireNonNull(status)).body(Map.of("message", message));
    }
}
