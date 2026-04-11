package com.teacher.backend.repository;

import java.util.Optional;

import com.teacher.backend.entity.KnowledgePointDiscussionLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgePointDiscussionLikeRepository extends JpaRepository<KnowledgePointDiscussionLike, Long> {

    long countByPost_Id(Long postId);

    boolean existsByPost_IdAndUser_Id(Long postId, Long userId);

    Optional<KnowledgePointDiscussionLike> findByPost_IdAndUser_Id(Long postId, Long userId);

    void deleteByPost_IdAndUser_Id(Long postId, Long userId);

    void deleteByPost_Id(Long postId);
}
