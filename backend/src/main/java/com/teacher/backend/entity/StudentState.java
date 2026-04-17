package com.teacher.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "student_states", uniqueConstraints = {@UniqueConstraint(name = "uk_student_state_user", columnNames = {"user_id"})})
public class StudentState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 40)
    private String major = "";

    @Column(name = "course_name", nullable = false, length = 120)
    private String courseName = "";

    @Column(name = "learning_records_json", columnDefinition = "TEXT")
    private String learningRecordsJson = "[]";

    @Column(name = "wrong_book_json", columnDefinition = "TEXT")
    private String wrongBookJson = "[]";

    @Column(name = "joined_courses_json", columnDefinition = "TEXT")
    private String joinedCoursesJson = "[]";

    @Column(name = "completed_resource_keys_json", columnDefinition = "TEXT")
    private String completedResourceKeysJson = "[]";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (learningRecordsJson == null) {
            learningRecordsJson = "[]";
        }
        if (wrongBookJson == null) {
            wrongBookJson = "[]";
        }
        if (joinedCoursesJson == null) {
            joinedCoursesJson = "[]";
        }
        if (completedResourceKeysJson == null) {
            completedResourceKeysJson = "[]";
        }
        if (major == null) {
            major = "";
        }
        if (courseName == null) {
            courseName = "";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (major == null) {
            major = "";
        }
        if (courseName == null) {
            courseName = "";
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getLearningRecordsJson() {
        return learningRecordsJson;
    }

    public void setLearningRecordsJson(String learningRecordsJson) {
        this.learningRecordsJson = learningRecordsJson;
    }

    public String getWrongBookJson() {
        return wrongBookJson;
    }

    public void setWrongBookJson(String wrongBookJson) {
        this.wrongBookJson = wrongBookJson;
    }

    public String getJoinedCoursesJson() {
        return joinedCoursesJson;
    }

    public void setJoinedCoursesJson(String joinedCoursesJson) {
        this.joinedCoursesJson = joinedCoursesJson;
    }

    public String getCompletedResourceKeysJson() {
        return completedResourceKeysJson;
    }

    public void setCompletedResourceKeysJson(String completedResourceKeysJson) {
        this.completedResourceKeysJson = completedResourceKeysJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

}
