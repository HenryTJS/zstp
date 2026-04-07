package com.teacher.backend.repository;

import com.teacher.backend.entity.KnowledgePointTestSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgePointTestSubmissionRepository extends JpaRepository<KnowledgePointTestSubmission, Long> {
    boolean existsByTestIdAndStudentUserId(Long testId, Long studentUserId);
    Optional<KnowledgePointTestSubmission> findByTestIdAndStudentUserId(Long testId, Long studentUserId);
    List<KnowledgePointTestSubmission> findByTestId(Long testId);
}

