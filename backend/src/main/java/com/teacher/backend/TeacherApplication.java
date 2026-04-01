package com.teacher.backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TeacherApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeacherApplication.class, args);
    }

    @Bean
    CommandLineRunner ensureUploadDirectory(@Value("${app.upload-dir:uploads}") String uploadDir) {
        return args -> {
            Path uploadPath = Paths.get(uploadDir);
            try {
                Files.createDirectories(uploadPath);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to create upload directory: " + uploadPath, exception);
            }
        };
    }
}
