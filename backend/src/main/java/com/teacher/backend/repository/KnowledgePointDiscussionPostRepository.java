package com.teacher.backend.repository;

import java.util.List;

import com.teacher.backend.entity.KnowledgePointDiscussionPost;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.EntityGraph;

public interface KnowledgePointDiscussionPostRepository extends JpaRepository<KnowledgePointDiscussionPost, Long> {

    @EntityGraph(attributePaths = {"author", "replyToUser", "parent", "parent.author"})
    List<KnowledgePointDiscussionPost> findByKnowledgePoint_IdOrderByCreatedAtAsc(Long knowledgePointId);

    List<KnowledgePointDiscussionPost> findByParent_Id(Long parentId);
}
