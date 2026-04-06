package com.teacher.backend.repository;

import java.util.Optional;

import com.teacher.backend.entity.KnowledgePointPublishedTest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgePointPublishedTestRepository extends JpaRepository<KnowledgePointPublishedTest, Long> {

    Optional<KnowledgePointPublishedTest> findByCourseNameAndPointName(String courseName, String pointName);
}
