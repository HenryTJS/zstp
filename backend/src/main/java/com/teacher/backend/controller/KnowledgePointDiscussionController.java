package com.teacher.backend.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import com.teacher.backend.dto.CreateKnowledgePointDiscussionRequest;
import com.teacher.backend.dto.ToggleKnowledgePointDiscussionLikeRequest;
import com.teacher.backend.entity.CourseKnowledgePoint;
import com.teacher.backend.entity.KnowledgePointDiscussionLike;
import com.teacher.backend.entity.KnowledgePointDiscussionPost;
import com.teacher.backend.entity.User;
import com.teacher.backend.repository.CourseKnowledgePointRepository;
import com.teacher.backend.repository.KnowledgePointDiscussionLikeRepository;
import com.teacher.backend.repository.KnowledgePointDiscussionPostRepository;
import com.teacher.backend.repository.UserRepository;
import com.teacher.backend.service.DiscussionNotificationService;
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
@RequestMapping("/api/knowledge-point-discussions")
public class KnowledgePointDiscussionController {

    private static final Set<String> ALLOWED_ROLES = Set.of("student", "teacher", "admin");
    private static final Set<String> VALID_POST_KINDS = Set.of("NORMAL", "QA", "DISCUSSION");
    private static final int MAX_CONTENT = 8000;

    private final CourseKnowledgePointRepository courseKnowledgePointRepository;
    private final KnowledgePointDiscussionPostRepository postRepository;
    private final KnowledgePointDiscussionLikeRepository likeRepository;
    private final UserRepository userRepository;
    private final DiscussionNotificationService discussionNotificationService;

    public KnowledgePointDiscussionController(
        CourseKnowledgePointRepository courseKnowledgePointRepository,
        KnowledgePointDiscussionPostRepository postRepository,
        KnowledgePointDiscussionLikeRepository likeRepository,
        UserRepository userRepository,
        DiscussionNotificationService discussionNotificationService
    ) {
        this.courseKnowledgePointRepository = courseKnowledgePointRepository;
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.userRepository = userRepository;
        this.discussionNotificationService = discussionNotificationService;
    }

    /**
     * 列表必须在同一持久化会话内组装树（访问 parent/author），且项目关闭了 open-in-view，
     * 因此此处使用只读事务，避免 LazyInitializationException 导致 500。
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> list(
        @RequestParam String courseName,
        @RequestParam String pointName,
        @RequestParam(required = false) Long userId
    ) {
        String cn = normalize(courseName);
        String pn = normalize(pointName);
        if (!StringUtils.hasText(cn) || !StringUtils.hasText(pn)) {
            return error(HttpStatus.BAD_REQUEST, "courseName 与 pointName 不能为空");
        }
        Optional<CourseKnowledgePoint> kpOpt = courseKnowledgePointRepository.findByCourseNameAndPointName(cn, pn);
        if (kpOpt.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        Long kpId = kpOpt.get().getId();
        List<KnowledgePointDiscussionPost> flat = postRepository.findByKnowledgePoint_IdOrderByCreatedAtAsc(kpId);
        if (flat.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        Map<Long, Long> likeCounts = new HashMap<>();
        Map<Long, Boolean> likedByMe = new HashMap<>();
        List<Long> ids = flat.stream().map(KnowledgePointDiscussionPost::getId).toList();
        for (Long id : ids) {
            likeCounts.put(id, likeRepository.countByPost_Id(id));
            if (userId != null) {
                likedByMe.put(id, likeRepository.existsByPost_IdAndUser_Id(id, userId));
            }
        }

        Map<Long, List<KnowledgePointDiscussionPost>> byParent = new HashMap<>();
        for (KnowledgePointDiscussionPost p : flat) {
            Long parentKey = p.getParent() == null ? null : p.getParent().getId();
            byParent.computeIfAbsent(parentKey, k -> new ArrayList<>()).add(p);
        }
        for (List<KnowledgePointDiscussionPost> list : byParent.values()) {
            list.sort(Comparator.comparing(KnowledgePointDiscussionPost::getCreatedAt));
        }

        List<KnowledgePointDiscussionPost> roots = byParent.getOrDefault(null, List.of());
        roots.sort(Comparator.comparing(KnowledgePointDiscussionPost::getCreatedAt).reversed());

        List<Map<String, Object>> out = new ArrayList<>();
        for (KnowledgePointDiscussionPost root : roots) {
            out.add(toNode(root, byParent, likeCounts, likedByMe, userId));
        }
        return ResponseEntity.ok(out);
    }

    @GetMapping("/count-by-user")
    @Transactional(readOnly = true)
    public ResponseEntity<?> countByUser(@RequestParam Long userId) {
        if (userId == null) {
            return error(HttpStatus.BAD_REQUEST, "userId 必填");
        }
        long count = postRepository.countByAuthor_Id(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    private Map<String, Object> toNode(
        KnowledgePointDiscussionPost p,
        Map<Long, List<KnowledgePointDiscussionPost>> byParent,
        Map<Long, Long> likeCounts,
        Map<Long, Boolean> likedByMe,
        Long currentUserId
    ) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("content", p.getContent());
        String pk = p.getPostKind();
        m.put("postKind", pk == null || pk.isEmpty() ? "NORMAL" : pk);
        m.put("createdAt", p.getCreatedAt() == null ? null : p.getCreatedAt().toString());
        m.put("author", authorMap(p.getAuthor()));
        User replyTarget = p.getReplyToUser();
        if (replyTarget == null && p.getParent() != null) {
            replyTarget = p.getParent().getAuthor();
        }
        m.put("replyTo", replyTarget == null ? null : authorMap(replyTarget));
        m.put("likeCount", likeCounts.getOrDefault(p.getId(), 0L));
        m.put("likedByMe", currentUserId != null && Boolean.TRUE.equals(likedByMe.get(p.getId())));
        List<KnowledgePointDiscussionPost> children = new ArrayList<>(byParent.getOrDefault(p.getId(), List.of()));
        children.sort(Comparator.comparing(KnowledgePointDiscussionPost::getCreatedAt));
        List<Map<String, Object>> replyMaps = new ArrayList<>();
        for (KnowledgePointDiscussionPost c : children) {
            replyMaps.add(toNode(c, byParent, likeCounts, likedByMe, currentUserId));
        }
        m.put("replies", replyMaps);
        return m;
    }

    private Map<String, Object> authorMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (u == null) {
            m.put("id", null);
            m.put("username", "");
            m.put("role", "");
            return m;
        }
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("role", u.getRole());
        return m;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody(required = false) CreateKnowledgePointDiscussionRequest request) {
        if (request == null || request.userId() == null) {
            return error(HttpStatus.BAD_REQUEST, "userId 必填");
        }
        User author = userRepository.findById(request.userId()).orElse(null);
        if (author == null || !ALLOWED_ROLES.contains(author.getRole())) {
            return error(HttpStatus.FORBIDDEN, "无权限发帖");
        }
        String cn = normalize(request.courseName());
        String pn = normalize(request.pointName());
        String content = request.content() == null ? "" : request.content().trim();
        if (!StringUtils.hasText(cn) || !StringUtils.hasText(pn)) {
            return error(HttpStatus.BAD_REQUEST, "courseName 与 pointName 不能为空");
        }
        if (!StringUtils.hasText(content) || content.length() > MAX_CONTENT) {
            return error(HttpStatus.BAD_REQUEST, "内容 1～" + MAX_CONTENT + " 字");
        }
        CourseKnowledgePoint kp = courseKnowledgePointRepository.findByCourseNameAndPointName(cn, pn).orElse(null);
        if (kp == null) {
            return error(HttpStatus.NOT_FOUND, "知识点不存在");
        }
        KnowledgePointDiscussionPost post = new KnowledgePointDiscussionPost();
        post.setKnowledgePoint(kp);
        post.setAuthor(author);
        post.setContent(content);
        String postKind = "NORMAL";
        if (request.parentId() != null) {
            KnowledgePointDiscussionPost parent = postRepository.findById(request.parentId()).orElse(null);
            if (parent == null || !parent.getKnowledgePoint().getId().equals(kp.getId())) {
                return error(HttpStatus.BAD_REQUEST, "父帖不存在或不属于该知识点");
            }
            post.setParent(parent);
            post.setReplyToUser(parent.getAuthor());
            postKind = "NORMAL";
        } else {
            post.setParent(null);
            post.setReplyToUser(null);
            String rawKind = request.postKind() == null ? "" : request.postKind().trim().toUpperCase();
            if (rawKind.isEmpty() || "NORMAL".equals(rawKind)) {
                postKind = "NORMAL";
            } else if (!VALID_POST_KINDS.contains(rawKind)) {
                return error(HttpStatus.BAD_REQUEST, "postKind 无效，应为 NORMAL、QA 或 DISCUSSION");
            } else if ("QA".equals(rawKind)) {
                if (!"student".equals(author.getRole())) {
                    return error(HttpStatus.FORBIDDEN, "仅学生可发答疑帖");
                }
                postKind = "QA";
            } else {
                if (!"teacher".equals(author.getRole()) && !"admin".equals(author.getRole())) {
                    return error(HttpStatus.FORBIDDEN, "仅教师可发讨论帖");
                }
                postKind = "DISCUSSION";
            }
        }
        post.setPostKind(postKind);
        post = postRepository.save(post);
        String courseName = kp.getCourseName();
        String pointName = kp.getPointName();
        if (post.getParent() == null) {
            if ("QA".equals(postKind)) {
                discussionNotificationService.notifyTeachersNewQa(
                    author.getId(), courseName, pointName, post.getId(), author.getUsername());
            } else if ("DISCUSSION".equals(postKind)) {
                discussionNotificationService.notifyStudentsNewDiscussion(
                    author.getId(), courseName, pointName, post.getId(), author.getUsername());
            }
        } else {
            KnowledgePointDiscussionPost parent = post.getParent();
            Long parentAuthorId = parent.getAuthor() == null ? null : parent.getAuthor().getId();
            discussionNotificationService.notifyReply(
                parentAuthorId, author.getId(), author.getUsername(), courseName, pointName, post.getId());
        }
        return ResponseEntity.ok(Map.of(
            "message", "ok",
            "id", post.getId()
        ));
    }

    @PostMapping("/{postId}/like")
    @Transactional
    public ResponseEntity<?> toggleLike(
        @PathVariable Long postId,
        @RequestBody(required = false) ToggleKnowledgePointDiscussionLikeRequest body
    ) {
        Long userId = body == null ? null : body.userId();
        if (userId == null) {
            return error(HttpStatus.BAD_REQUEST, "userId 必填");
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !ALLOWED_ROLES.contains(user.getRole())) {
            return error(HttpStatus.FORBIDDEN, "无权限");
        }
        KnowledgePointDiscussionPost post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            return error(HttpStatus.NOT_FOUND, "帖子不存在");
        }
        Optional<KnowledgePointDiscussionLike> existing = likeRepository.findByPost_IdAndUser_Id(postId, userId);
        boolean added = false;
        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
        } else {
            KnowledgePointDiscussionLike like = new KnowledgePointDiscussionLike();
            like.setPost(post);
            like.setUser(user);
            likeRepository.save(like);
            added = true;
        }
        if (added && post.getAuthor() != null) {
            CourseKnowledgePoint kp = post.getKnowledgePoint();
            discussionNotificationService.notifyLike(
                post.getAuthor().getId(),
                userId,
                user.getUsername(),
                kp.getCourseName(),
                kp.getPointName(),
                postId);
        }
        long count = likeRepository.countByPost_Id(postId);
        boolean liked = likeRepository.existsByPost_IdAndUser_Id(postId, userId);
        return ResponseEntity.ok(Map.of("likeCount", count, "likedByMe", liked));
    }

    /**
     * 发帖人删除自己的帖子（含其下全部回复）；学生/教师/管理员均仅可删本人内容。
     */
    @DeleteMapping("/{postId}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long postId, @RequestParam Long userId) {
        if (userId == null) {
            return error(HttpStatus.BAD_REQUEST, "userId 必填");
        }
        User actor = userRepository.findById(userId).orElse(null);
        if (actor == null || !ALLOWED_ROLES.contains(actor.getRole())) {
            return error(HttpStatus.FORBIDDEN, "无权限");
        }
        KnowledgePointDiscussionPost post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            return error(HttpStatus.NOT_FOUND, "帖子不存在");
        }
        User author = post.getAuthor();
        if (author == null || author.getId() == null || !author.getId().equals(userId)) {
            return error(HttpStatus.FORBIDDEN, "只能删除自己发表的内容");
        }
        deletePostSubtree(post);
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    private void deletePostSubtree(KnowledgePointDiscussionPost post) {
        List<KnowledgePointDiscussionPost> children = postRepository.findByParent_Id(post.getId());
        for (KnowledgePointDiscussionPost c : children) {
            deletePostSubtree(c);
        }
        likeRepository.deleteByPost_Id(post.getId());
        postRepository.delete(post);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(Objects.requireNonNull(status)).body(Map.of("message", message));
    }
}
