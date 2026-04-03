package com.teacher.backend.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.teacher.backend.entity.CourseCatalogEntry;
import com.teacher.backend.repository.CourseCatalogEntryRepository;
import com.teacher.backend.repository.CourseKnowledgePointPrereqRepository;
import com.teacher.backend.repository.CourseKnowledgePointRepository;
import com.teacher.backend.repository.TeacherCoursePermissionRepository;

@Service
public class CourseCatalogService {

    private static final List<String> DEFAULT_COURSES = List.of(
        "高等数学",
        "线性代数与解析几何",
        "概率论与数理统计",
        "复变函数与积分变换",
        "数学物理方程"
    );

    private static final Map<String, String> ALIASES = buildAliases();

    private final CourseCatalogEntryRepository courseCatalogEntryRepository;
    private final CourseKnowledgePointRepository courseKnowledgePointRepository;
    private final CourseKnowledgePointPrereqRepository courseKnowledgePointPrereqRepository;
    private final TeacherCoursePermissionRepository teacherCoursePermissionRepository;

    public CourseCatalogService(CourseCatalogEntryRepository courseCatalogEntryRepository,
                                 CourseKnowledgePointRepository courseKnowledgePointRepository,
                                 CourseKnowledgePointPrereqRepository courseKnowledgePointPrereqRepository,
                                 TeacherCoursePermissionRepository teacherCoursePermissionRepository) {
        this.courseCatalogEntryRepository = courseCatalogEntryRepository;
        this.courseKnowledgePointRepository = courseKnowledgePointRepository;
        this.courseKnowledgePointPrereqRepository = courseKnowledgePointPrereqRepository;
        this.teacherCoursePermissionRepository = teacherCoursePermissionRepository;
        ensureDefaultsSeeded();
    }

    private synchronized void ensureDefaultsSeeded() {
        if (courseCatalogEntryRepository == null) return;
        if (courseCatalogEntryRepository.findAllByOrderBySortOrderAscIdAsc().isEmpty()) {
            int idx = 0;
            for (String c : DEFAULT_COURSES) {
                CourseCatalogEntry e = new CourseCatalogEntry();
                e.setCourseName(c);
                e.setSortOrder(idx++);
                courseCatalogEntryRepository.save(e);
            }
        }
    }

    public List<String> allCourses() {
        ensureDefaultsSeeded();
        return courseCatalogEntryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
            .map(CourseCatalogEntry::getCourseName)
            .toList();
    }

    public String defaultCourse() {
        List<String> courses = allCourses();
        return courses.isEmpty() ? DEFAULT_COURSES.get(0) : courses.get(0);
    }

    public String normalizeCourseName(String rawCourseName) {
        if (!StringUtils.hasText(rawCourseName)) {
            return defaultCourse();
        }

        String trimmed = rawCourseName.trim();
        List<String> knownCourses = allCourses();

        // 直接命中
        for (String c : knownCourses) {
            if (c != null && c.equals(trimmed)) return trimmed;
        }

        // 别名命中
        String alias = ALIASES.get(trimmed.toLowerCase(Locale.ROOT));
        if (alias != null) {
            // 别名映射到的课程名也可能未在 DB 中（极端情况），此时仍返回 alias
            return alias;
        }

        // 包含关系兜底（兼容旧数据：例如“高数”可能在字符串里带其它描述）
        for (String course : knownCourses) {
            if (course != null && trimmed.contains(course)) {
                return course;
            }
        }

        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            if (trimmed.toLowerCase(Locale.ROOT).contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 对于“未预先存在于课程目录”的名称，允许直接作为新课程名使用；
        // 这样管理员新增课程、再分配权限、教师查看才会串起来。
        return trimmed;
    }

    public List<String> addCourse(String rawCourseName) {
        if (!StringUtils.hasText(rawCourseName)) {
            return allCourses();
        }

        String normalized = normalizeCourseName(rawCourseName);

        // normalized 可能命中了 defaultCourse（用户输入空或未知），这里仍允许它存在
        if (!courseCatalogEntryRepository.existsByCourseNameIgnoreCase(normalized)) {
            int maxSort = courseCatalogEntryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .mapToInt(CourseCatalogEntry::getSortOrder)
                .max()
                .orElse(-1);
            CourseCatalogEntry e = new CourseCatalogEntry();
            e.setCourseName(normalized);
            e.setSortOrder(maxSort + 1);
            courseCatalogEntryRepository.save(e);
        }

        return allCourses();
    }

    public List<String> deleteCourse(String rawCourseName) {
        if (!StringUtils.hasText(rawCourseName)) return allCourses();
        String normalized = normalizeCourseName(rawCourseName);

        // 先清理与课程绑定的数据，避免后续 teacher/view 使用残留
        courseKnowledgePointPrereqRepository.deleteByCourseName(normalized);
        courseKnowledgePointRepository.deleteByCourseName(normalized);
        teacherCoursePermissionRepository.deleteByCourseName(normalized);
        courseCatalogEntryRepository.deleteByCourseNameIgnoreCase(normalized);

        return allCourses();
    }

    private static Map<String, String> buildAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("高数", "高等数学");
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
