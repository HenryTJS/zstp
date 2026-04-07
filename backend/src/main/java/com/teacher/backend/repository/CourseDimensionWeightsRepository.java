package com.teacher.backend.repository;

import java.util.Optional;

import com.teacher.backend.entity.CourseDimensionWeights;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseDimensionWeightsRepository extends JpaRepository<CourseDimensionWeights, Long> {
    Optional<CourseDimensionWeights> findByCourseName(String courseName);
}

