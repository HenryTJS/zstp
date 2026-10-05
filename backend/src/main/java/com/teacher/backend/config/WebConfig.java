package com.teacher.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
            // Dev + production origins (same-origin calls won't require CORS, but browsers
            // still send Origin on fetch; reject here would cause 403 "Invalid CORS request").
            //
            // 使用 allowedOriginPatterns 而非 allowedOrigins：
            // 开发时通过局域网 IP（如 http://10.x.x.x:5173）访问前端时，浏览器会带上该 IP 的
            // Origin 头，若不在白名单内后端会返回 403 Invalid CORS request，导致"localhost 能登录、
            // IP 地址无法登录"。这里放通本机回环地址与常见内网网段，同时保留线上域名。
            // 注意：allowCredentials(false) 为前提，故可安全使用通配。
            .allowedOriginPatterns(
                "http://localhost:[*]",
                "https://localhost:[*]",
                "http://127.0.0.1:[*]",
                "http://10.*",
                "http://192.168.*",
                "http://172.*",
                "http://zstp.top",
                "http://www.zstp.top",
                "https://zstp.top",
                "https://www.zstp.top"
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false);
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // 使上传的文件可通过 /uploads/** 直接访问
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations("file:" + uploadDir + "/");
    }
}
