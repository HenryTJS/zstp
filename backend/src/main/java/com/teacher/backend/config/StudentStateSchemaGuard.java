package com.teacher.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 迁移保护：避免旧库缺列导致登录/查询直接 500。
 * 当前只兜底 student_states.total_learning_seconds，语句幂等可重复执行。
 */
@Component
public class StudentStateSchemaGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StudentStateSchemaGuard.class);

    private final JdbcTemplate jdbcTemplate;

    public StudentStateSchemaGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(
                "ALTER TABLE student_states " +
                "ADD COLUMN IF NOT EXISTS total_learning_seconds BIGINT NOT NULL DEFAULT 0"
            );
            log.info("Schema guard checked: student_states.total_learning_seconds");
        } catch (Exception ex) {
            log.warn("Schema guard failed for student_states.total_learning_seconds: {}", ex.getMessage());
        }
    }
}
