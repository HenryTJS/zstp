package com.teacher.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "course_dimension_weights",
    uniqueConstraints = {@UniqueConstraint(name = "uk_course_dimension_weights_course", columnNames = {"course_name"})}
)
public class CourseDimensionWeights {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_name", nullable = false, length = 120)
    private String courseName;

    @Column(name = "logic_reasoning", nullable = false)
    private double logicReasoning;

    @Column(name = "numeric_calculation", nullable = false)
    private double numericCalculation;

    @Column(name = "semantic_understanding", nullable = false)
    private double semanticUnderstanding;

    @Column(name = "spatial_imagination", nullable = false)
    private double spatialImagination;

    @Column(name = "memory_retrieval", nullable = false)
    private double memoryRetrieval;

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
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

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

    public double getLogicReasoning() {
        return logicReasoning;
    }

    public void setLogicReasoning(double logicReasoning) {
        this.logicReasoning = logicReasoning;
    }

    public double getNumericCalculation() {
        return numericCalculation;
    }

    public void setNumericCalculation(double numericCalculation) {
        this.numericCalculation = numericCalculation;
    }

    public double getSemanticUnderstanding() {
        return semanticUnderstanding;
    }

    public void setSemanticUnderstanding(double semanticUnderstanding) {
        this.semanticUnderstanding = semanticUnderstanding;
    }

    public double getSpatialImagination() {
        return spatialImagination;
    }

    public void setSpatialImagination(double spatialImagination) {
        this.spatialImagination = spatialImagination;
    }

    public double getMemoryRetrieval() {
        return memoryRetrieval;
    }

    public void setMemoryRetrieval(double memoryRetrieval) {
        this.memoryRetrieval = memoryRetrieval;
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

