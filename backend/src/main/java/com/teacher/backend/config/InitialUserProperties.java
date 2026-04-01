package com.teacher.backend.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class InitialUserProperties {

    private List<SeedUser> bootstrapUsers = new ArrayList<>();

    public List<SeedUser> getBootstrapUsers() {
        return bootstrapUsers;
    }

    public void setBootstrapUsers(List<SeedUser> bootstrapUsers) {
        this.bootstrapUsers = bootstrapUsers;
    }

    public static class SeedUser {
        private String username;
        private String email;
        private String password;
        private String role;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}