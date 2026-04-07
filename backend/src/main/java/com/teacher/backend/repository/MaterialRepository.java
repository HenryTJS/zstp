
package com.teacher.backend.repository;

import java.util.List;

import com.teacher.backend.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    List<Material> findAllByOrderByCreatedAtDesc();

    List<Material> findTop8ByOrderByCreatedAtDesc();

    // 查找指定知识点的所有资料
    List<Material> findByCourseNameAndKnowledgePoint(String courseName, String knowledgePoint);

    // 查找知识点列表中任意一个的资料
    List<Material> findByCourseNameAndKnowledgePointIn(String courseName, List<String> knowledgePoints);

    long countByCourseName(String courseName);
    
}
