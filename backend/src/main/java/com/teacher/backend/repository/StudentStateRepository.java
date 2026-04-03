package com.teacher.backend.repository;

import java.util.Optional;

import com.teacher.backend.entity.StudentState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentStateRepository extends JpaRepository<StudentState, Long> {

    @Query("SELECT s FROM StudentState s WHERE s.user.id = :userId")
    Optional<StudentState> findByUserId(@Param("userId") Long userId);
}
