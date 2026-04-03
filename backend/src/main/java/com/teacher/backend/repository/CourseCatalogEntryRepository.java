package com.teacher.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.teacher.backend.entity.CourseCatalogEntry;

public interface CourseCatalogEntryRepository extends JpaRepository<CourseCatalogEntry, Long> {
    List<CourseCatalogEntry> findAllByOrderBySortOrderAscIdAsc();

    Optional<CourseCatalogEntry> findByCourseNameIgnoreCase(String courseName);

    boolean existsByCourseNameIgnoreCase(String courseName);

    void deleteByCourseNameIgnoreCase(String courseName);
}

