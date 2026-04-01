package com.teacher.backend.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CourseCatalogService {

    private static final List<String> COURSES = List.of(
        "高等数学",
        "线性代数与解析几何",
        "概率论与数理统计",
        "复变函数与积分变换",
        "数学物理方程"
    );

    private static final Map<String, String> ALIASES = buildAliases();

    public List<String> allCourses() {
        return COURSES;
    }

    public String defaultCourse() {
        return COURSES.get(0);
    }

    public String normalizeCourseName(String rawCourseName) {
        if (!StringUtils.hasText(rawCourseName)) {
            return defaultCourse();
        }

        String trimmed = rawCourseName.trim();
        if (COURSES.contains(trimmed)) {
            return trimmed;
        }

        String alias = ALIASES.get(trimmed.toLowerCase(Locale.ROOT));
        if (alias != null) {
            return alias;
        }

        for (String course : COURSES) {
            if (trimmed.contains(course)) {
                return course;
            }
        }

        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            if (trimmed.toLowerCase(Locale.ROOT).contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return defaultCourse();
    }

    private static Map<String, String> buildAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("高数", "高等数学");
        aliases.put("高等数学", "高等数学");
        aliases.put("线代", "线性代数与解析几何");
        aliases.put("线性代数", "线性代数与解析几何");
        aliases.put("线性代数与解析几何", "线性代数与解析几何");
        aliases.put("概率", "概率论与数理统计");
        aliases.put("概率论", "概率论与数理统计");
        aliases.put("概率论与数理统计", "概率论与数理统计");
        aliases.put("复变", "复变函数与积分变换");
        aliases.put("复变函数", "复变函数与积分变换");
        aliases.put("复变函数与积分变换", "复变函数与积分变换");
        aliases.put("数理方程", "数学物理方程");
        aliases.put("数学物理方程", "数学物理方程");
        return aliases;
    }
}
