package com.teacher.backend.config;

import java.net.URI;
import java.net.URISyntaxException;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration
public class DataSourceConfig {

    @Bean
    DataSource dataSource(Environment environment) {
        DatabaseSettings settings = resolveDatabaseSettings(environment);
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setJdbcUrl(settings.jdbcUrl());
        if (StringUtils.hasText(settings.username())) {
            dataSource.setUsername(settings.username());
        }
        if (StringUtils.hasText(settings.password())) {
            dataSource.setPassword(settings.password());
        }
        return dataSource;
    }

    private DatabaseSettings resolveDatabaseSettings(Environment environment) {
        String direct = trim(environment.getProperty("DATABASE_URL"));
        String user = trim(environment.getProperty("POSTGRES_USER", "postgres"));
        String password = trim(environment.getProperty("POSTGRES_PASSWORD", "2022S3414ycx"));

        if (StringUtils.hasText(direct)) {
            if (direct.startsWith("jdbc:postgresql://")) {
                return new DatabaseSettings(direct, user, password);
            }
            ParsedUrl parsedUrl = parseDatabaseUrl(direct);
            String jdbcUrl = "jdbc:postgresql://" + parsedUrl.host() + ":" + parsedUrl.port() + "/" + parsedUrl.database();
            String resolvedUser = StringUtils.hasText(user) ? user : parsedUrl.username();
            String resolvedPassword = StringUtils.hasText(password) ? password : parsedUrl.password();
            return new DatabaseSettings(jdbcUrl, resolvedUser, resolvedPassword);
        }

        String host = environment.getProperty("POSTGRES_HOST", "localhost");
        String port = environment.getProperty("POSTGRES_PORT", "5432");
        String database = environment.getProperty("POSTGRES_DB", "ai_self_learning");
        return new DatabaseSettings("jdbc:postgresql://" + host + ":" + port + "/" + database, user, password);
    }

    private ParsedUrl parseDatabaseUrl(String rawUrl) {
        String normalized = rawUrl
            .replaceFirst("^postgresql\\+psycopg2://", "postgresql://")
            .replaceFirst("^postgres://", "postgresql://");

        try {
            URI uri = new URI(normalized);
            String[] userInfo = uri.getUserInfo() == null ? new String[0] : uri.getUserInfo().split(":", 2);
            String username = userInfo.length > 0 ? userInfo[0] : "";
            String password = userInfo.length > 1 ? userInfo[1] : "";
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            if (!StringUtils.hasText(uri.getHost())) {
                throw new IllegalStateException("DATABASE_URL host is missing");
            }
            String database = uri.getPath() == null ? "ai_self_learning" : uri.getPath().replaceFirst("^/", "");
            return new ParsedUrl(uri.getHost(), port, database, username, password);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Invalid DATABASE_URL: " + rawUrl, exception);
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private record DatabaseSettings(String jdbcUrl, String username, String password) {
    }

    private record ParsedUrl(String host, int port, String database, String username, String password) {
    }
}
