package com.teacher.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "kp_test_submissions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_kp_test_submission_test_student", columnNames = {"test_id", "student_user_id"})
        }
)
public class KnowledgePointTestSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "test_id", nullable = false)
    private Long testId;

    @Column(name = "course_name", nullable = false, length = 120)
    private String courseName;

    @Column(name = "point_name", nullable = false, length = 120)
    private String pointName;

    @Column(name = "student_user_id", nullable = false)
    private Long studentUserId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "total_score", nullable = false)
    private Integer totalScore;

    @Column(name = "full_score", nullable = false)
    private Integer fullScore;

    @Column(name = "per_question_json", columnDefinition = "TEXT", nullable = false)
    private String perQuestionJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTestId() {
        return testId;
    }

    public void setTestId(Long testId) {
        this.testId = testId;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getPointName() {
        return pointName;
    }

    public void setPointName(String pointName) {
        this.pointName = pointName;
    }

    public Long getStudentUserId() {
        return studentUserId;
    }

    public void setStudentUserId(Long studentUserId) {
        this.studentUserId = studentUserId;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    public Integer getFullScore() {
        return fullScore;
    }

    public void setFullScore(Integer fullScore) {
        this.fullScore = fullScore;
    }

    public String getPerQuestionJson() {
        return perQuestionJson;
    }

    public void setPerQuestionJson(String perQuestionJson) {
        this.perQuestionJson = perQuestionJson;
    }
}

