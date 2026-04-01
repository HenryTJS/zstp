package com.teacher.backend.repository;

import java.util.List;

import com.teacher.backend.entity.CourseKnowledgePointPrereq;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseKnowledgePointPrereqRepository extends JpaRepository<CourseKnowledgePointPrereq, Long> {

    List<CourseKnowledgePointPrereq> findByCourseName(String courseName);

    void deleteByCourseName(String courseName);

    List<CourseKnowledgePointPrereq> findByPointId(Long pointId);

    void deleteByPointId(Long pointId);
    void deleteByPrereqPointId(Long prereqPointId);
}
