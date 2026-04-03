package com.teacher.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.teacher.backend.entity.TeacherCoursePermissionRequest;

public interface TeacherCoursePermissionRequestRepository extends JpaRepository<TeacherCoursePermissionRequest, Long> {
    List<TeacherCoursePermissionRequest> findByTeacherIdOrderByCreatedAtDesc(Long teacherId);

    List<TeacherCoursePermissionRequest> findByStatusOrderByCreatedAtDesc(String status);

    Optional<TeacherCoursePermissionRequest> findFirstByTeacherIdAndCourseNameAndStatusOrderByCreatedAtDesc(
        Long teacherId,
        String courseName,
        String status
    );
}

