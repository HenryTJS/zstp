package com.teacher.backend.config;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.teacher.backend.util.JwtUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JWT authentication filter.
 * 
 * Extracts the Bearer token from the Authorization header, validates it,
 * and sets the userId and role as request attributes for downstream controllers.
 * 
 * Public paths (login, health check, etc.) are excluded from authentication.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    /**
     * Path prefixes that do NOT require authentication.
     */
    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api/users/login",
        "/api/health"
    );

    /**
     * Path prefixes that are always public (GET requests for static-like resources).
     */
    private static final List<String> PUBLIC_PREFIXES = List.of(
        "/api/exams/", // download rendered exam files
        "/uploads/"    // uploaded files (avatars, covers, etc.) are public
    );

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip authentication for public paths
        if (isPublicPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7).trim();
        if (!jwtUtil.validateToken(token)) {
            sendUnauthorized(response, "Invalid or expired token");
            return;
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);

        if (userId == null) {
            sendUnauthorized(response, "Invalid token: missing userId");
            return;
        }

        // Set attributes for controllers to use
        request.setAttribute("currentUserId", userId);
        request.setAttribute("currentUserRole", role);

        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }

        // GET requests to exam download paths are public (for direct links)
        if ("GET".equalsIgnoreCase(method)) {
            for (String prefix : PUBLIC_PREFIXES) {
                if (path.startsWith(prefix)) {
                    return true;
                }
            }
        }

        // OPTIONS requests (CORS preflight) are always public
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        return false;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
