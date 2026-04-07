package com.teacher.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.lang.NonNull;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
            // Dev + production origins (same-origin calls won't require CORS, but browsers
            // still send Origin on fetch; reject here would cause 403 "Invalid CORS request").
            .allowedOrigins(
                "http://localhost:5173",
                "http://zstp.top",
                "http://www.zstp.top",
                "https://zstp.top",
                "https://www.zstp.top"
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false);
    }
}
