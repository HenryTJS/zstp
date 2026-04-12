package com.teacher.backend.repository;

import com.teacher.backend.entity.KnowledgePointTestSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgePointTestSubmissionRepository extends JpaRepository<KnowledgePointTestSubmission, Long> {
    boolean existsByTestIdAndStudentUserId(Long testId, Long studentUserId);
    Optional<KnowledgePointTestSubmission> findByTestIdAndStudentUserId(Long testId, Long studentUserId);
    List<KnowledgePointTestSubmission> findByTestId(Long testId);

    long countByCourseName(String courseName);

    @Query("SELECT COUNT(DISTINCT s.studentUserId) FROM KnowledgePointTestSubmission s WHERE s.courseName = :cn")
    long countDistinctStudentUserIdByCourseName(@Param("cn") String cn);

    @Query("SELECT AVG(s.totalScore) FROM KnowledgePointTestSubmission s WHERE s.courseName = :cn")
    Double avgTotalScoreByCourseName(@Param("cn") String cn);
}

