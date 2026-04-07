package com.teacher.backend.repository;

import java.util.List;

import com.teacher.backend.entity.CourseCreditRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseCreditRuleRepository extends JpaRepository<CourseCreditRule, Long> {
    List<CourseCreditRule> findByCourseNameOrderByIdAsc(String courseName);

    void deleteByCourseName(String courseName);
}
