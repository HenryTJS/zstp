package com.teacher.backend.config;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.teacher.backend.entity.User;
import com.teacher.backend.repository.UserRepository;
import com.teacher.backend.service.PasswordService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InitialUserSeeder implements CommandLineRunner {

    private static final Set<String> VALID_ROLES = Set.of("student", "teacher", "admin");

    private final InitialUserProperties initialUserProperties;
    private final UserRepository userRepository;
    private final PasswordService passwordService;

    public InitialUserSeeder(
        InitialUserProperties initialUserProperties,
        UserRepository userRepository,
        PasswordService passwordService
    ) {
        this.initialUserProperties = initialUserProperties;
        this.userRepository = userRepository;
        this.passwordService = passwordService;
    }

    @Override
    public void run(String... args) {
        for (InitialUserProperties.SeedUser seedUser : initialUserProperties.getBootstrapUsers()) {
            String username = normalize(seedUser.getUsername());
            String email = normalize(seedUser.getEmail()).toLowerCase(Locale.ROOT);
            String password = seedUser.getPassword() == null ? "" : seedUser.getPassword().trim();
            String role = normalize(seedUser.getRole());
            String workIdRaw = seedUser.getWorkId() == null ? "" : seedUser.getWorkId().trim();

            if (!StringUtils.hasText(username) || !StringUtils.hasText(email) || password.length() < 6 || !VALID_ROLES.contains(role)) {
                continue;
            }

            Optional<User> existingOpt = userRepository.findByUsernameIgnoreCase(username);
            if (existingOpt.isPresent()) {
                syncWorkIdOnExistingUser(existingOpt.get(), workIdRaw);
                continue;
            }

            if (StringUtils.hasText(workIdRaw) && userRepository.existsByWorkIdIgnoreCase(workIdRaw)) {
                continue;
            }

            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setRole(role);
            if (StringUtils.hasText(workIdRaw)) {
                user.setWorkId(workIdRaw);
            }
            user.setPasswordHash(passwordService.hashPassword(password));
            userRepository.save(user);
        }
    }

    /**
     * 预置账号若在添加学工号前已存在，启动时按配置补写 workId，便于学工号登录。
     */
    private void syncWorkIdOnExistingUser(User existing, String workIdFromConfig) {
        if (!StringUtils.hasText(workIdFromConfig)) {
            return;
        }
        if (workIdFromConfig.equals(existing.getWorkId())) {
            return;
        }
        Optional<User> holder = userRepository.findByWorkIdIgnoreCase(workIdFromConfig);
        if (holder.isPresent() && !holder.get().getId().equals(existing.getId())) {
            return;
        }
        existing.setWorkId(workIdFromConfig);
        userRepository.save(existing);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}