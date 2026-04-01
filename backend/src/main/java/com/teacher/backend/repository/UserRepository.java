package com.teacher.backend.repository;

import java.util.List;
import java.util.Optional;

import com.teacher.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);

    boolean existsByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);

    List<User> findAllByOrderByCreatedAtDesc();

    List<User> findAllByRoleOrderByCreatedAtDesc(String role);

    long countByRole(String role);

    Optional<User> findByIdAndRole(Long id, String role);

    List<User> findTop8ByOrderByCreatedAtDesc();
}
