package com.teacher.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.teacher.backend.entity.TeacherCoursePermission;

public interface TeacherCoursePermissionRepository extends JpaRepository<TeacherCoursePermission, Long> {
    List<TeacherCoursePermission> findByTeacherIdOrderByIdAsc(Long teacherId);

    boolean existsByTeacherIdAndCourseName(Long teacherId, String courseName);

    void deleteByTeacherId(Long teacherId);

    List<TeacherCoursePermission> findByTeacherIdAndCourseNameIn(Long teacherId, Collection<String> courseNames);

    void deleteByCourseName(String courseName);

    List<TeacherCoursePermission> findByCourseNameOrderByTeacherIdAsc(String courseName);
}

