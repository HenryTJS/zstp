package com.teacher.backend.config;

import java.util.Locale;
import java.util.Set;

import com.teacher.backend.entity.User;
import com.teacher.backend.repository.UserRepository;
import com.teacher.backend.service.PasswordService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InitialUserSeeder implements CommandLineRunner {

    private static final Set<String> VALID_ROLES = Set.of("student", "teacher");

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

            if (!StringUtils.hasText(username) || !StringUtils.hasText(email) || password.length() < 6 || !VALID_ROLES.contains(role)) {
                continue;
            }
            if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
                continue;
            }

            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setRole(role);
            user.setPasswordHash(passwordService.hashPassword(password));
            userRepository.save(user);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}