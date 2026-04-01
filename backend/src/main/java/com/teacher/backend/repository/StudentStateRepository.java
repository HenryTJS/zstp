package com.teacher.backend.repository;

import java.util.Optional;

import com.teacher.backend.entity.StudentState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentStateRepository extends JpaRepository<StudentState, Long> {

    Optional<StudentState> findByUserId(Long userId);
}
