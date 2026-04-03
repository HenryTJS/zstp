package com.teacher.backend.repository;

import java.util.List;
import java.util.Optional;

import com.teacher.backend.entity.CourseKnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseKnowledgePointRepository extends JpaRepository<CourseKnowledgePoint, Long> {

    List<CourseKnowledgePoint> findByCourseNameOrderBySortOrderAscIdAsc(String courseName);

    Optional<CourseKnowledgePoint> findByCourseNameAndPointName(String courseName, String pointName);

    void deleteByCourseName(String courseName);
}
