package com.teacher.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_knowledge_point_prereqs")
public class CourseKnowledgePointPrereq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_name", nullable = false, length = 120)
    private String courseName;

    @Column(name = "point_id", nullable = false)
    private Long pointId;

    @Column(name = "prereq_point_id", nullable = false)
    private Long prereqPointId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public Long getPointId() {
        return pointId;
    }

    public void setPointId(Long pointId) {
        this.pointId = pointId;
    }

    public Long getPrereqPointId() {
        return prereqPointId;
    }

    public void setPrereqPointId(Long prereqPointId) {
        this.prereqPointId = prereqPointId;
    }
}
