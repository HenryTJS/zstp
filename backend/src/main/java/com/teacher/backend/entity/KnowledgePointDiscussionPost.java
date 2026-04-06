package com.teacher.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "kp_discussion_posts")
public class KnowledgePointDiscussionPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_point_id", nullable = false)
    private CourseKnowledgePoint knowledgePoint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    /** 根帖 parent 为 null；回复指向父帖（可嵌套） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private KnowledgePointDiscussionPost parent;

    /** 被回复人（父帖作者）；根帖为 null，用于前端展示「谁回复了谁」 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_user_id")
    private User replyToUser;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 根帖：NORMAL | QA | DISCUSSION；回复恒为 NORMAL（库中可为空，旧数据兼容） */
    @Column(name = "post_kind", length = 32)
    private String postKind = "NORMAL";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (postKind == null || postKind.isBlank()) {
            postKind = "NORMAL";
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CourseKnowledgePoint getKnowledgePoint() {
        return knowledgePoint;
    }

    public void setKnowledgePoint(CourseKnowledgePoint knowledgePoint) {
        this.knowledgePoint = knowledgePoint;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public KnowledgePointDiscussionPost getParent() {
        return parent;
    }

    public void setParent(KnowledgePointDiscussionPost parent) {
        this.parent = parent;
    }

    public User getReplyToUser() {
        return replyToUser;
    }

    public void setReplyToUser(User replyToUser) {
        this.replyToUser = replyToUser;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPostKind() {
        return postKind;
    }

    public void setPostKind(String postKind) {
        this.postKind = postKind;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
