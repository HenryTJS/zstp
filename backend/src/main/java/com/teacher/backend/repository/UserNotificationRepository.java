package com.teacher.backend.repository;

import java.util.List;

import com.teacher.backend.entity.UserNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    List<UserNotification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadIsFalse(Long userId);

    @Modifying
    @Query("UPDATE UserNotification n SET n.read = true WHERE n.id = :id AND n.userId = :userId")
    int markRead(@Param("id") Long id, @Param("userId") Long userId);
}
