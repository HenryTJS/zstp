package com.teacher.backend.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.teacher.backend.dto.CreateAnnouncementRequest;
import com.teacher.backend.entity.Announcement;
import com.teacher.backend.entity.User;
import com.teacher.backend.entity.UserNotification;
import com.teacher.backend.repository.AnnouncementRepository;
import com.teacher.backend.repository.UserRepository;
import com.teacher.backend.repository.UserNotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private static final String ROLE_ADMIN = "admin";
    private static final String NOTIFY_TYPE_ANNOUNCEMENT = "ANNOUNCEMENT";

    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;
    private final UserNotificationRepository notificationRepository;

    public AnnouncementController(
        AnnouncementRepository announcementRepository,
        UserRepository userRepository,
        UserNotificationRepository notificationRepository
    ) {
        this.announcementRepository = announcementRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return announcementRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toMap)
            .toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody(required = false) CreateAnnouncementRequest request) {
        Long userId = request == null ? null : request.userId();
        if (!resolveAdmin(userId).isPresent()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "仅管理员可发布公告"));
        }
        String title = request == null || request.title() == null ? "" : request.title().trim();
        String content = request == null || request.content() == null ? "" : request.content().trim();
        if (!StringUtils.hasText(title)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "标题不能为空"));
        }
        if (!StringUtils.hasText(content)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "内容不能为空"));
        }
        Announcement a = new Announcement();
        a.setTitle(title);
        a.setContent(content);
        a.setPublisherId(userId);
        Announcement saved = announcementRepository.save(a);

        // Push as user_notifications so students/teachers/admin can read/delete/mark-read in the same "通知" UI.
        List<User> users = userRepository.findAll();
        for (User u : users) {
            if (u == null || u.getId() == null) {
                continue;
            }
            UserNotification n = new UserNotification();
            n.setUserId(u.getId());
            n.setType(NOTIFY_TYPE_ANNOUNCEMENT);
            n.setTitle(saved.getTitle());
            n.setBody(saved.getContent());
            n.setRead(false);
            n.setCourseName("");
            n.setPointName("");
            // For announcement notifications, reuse postId to store announcement id (so delete can cascade).
            n.setPostId(saved.getId());
            notificationRepository.save(n);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "ok", "announcement", toMap(saved)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable long id, @RequestParam(required = false) Long userId) {
        if (!resolveAdmin(userId).isPresent()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "仅管理员可删除公告"));
        }
        if (!announcementRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "公告不存在"));
        }
        announcementRepository.deleteById(id);
        notificationRepository.deleteByTypeAndPostId(NOTIFY_TYPE_ANNOUNCEMENT, id);
        return ResponseEntity.ok(Map.of("message", "已删除"));
    }

    private java.util.Optional<User> resolveAdmin(Long userId) {
        if (userId == null) {
            return java.util.Optional.empty();
        }
        return userRepository.findById(userId).filter(u -> ROLE_ADMIN.equals(u.getRole()));
    }

    private Map<String, Object> toMap(Announcement a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("title", a.getTitle());
        m.put("content", a.getContent());
        m.put("publisherId", a.getPublisherId());
        m.put("createdAt", a.getCreatedAt() == null ? null : a.getCreatedAt().toString());
        String publisherName = Optional.ofNullable(a.getPublisherId())
            .flatMap(userRepository::findById)
            .map(User::getUsername)
            .orElse(null);
        m.put("publisherName", publisherName);
        return m;
    }
}
