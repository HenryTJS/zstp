package com.teacher.backend.repository;

import java.util.Optional;

import com.teacher.backend.entity.MaterialText;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialTextRepository extends JpaRepository<MaterialText, Long> {

    Optional<MaterialText> findByMaterialId(Long materialId);

    void deleteByMaterialId(Long materialId);
}
