package com.teacher.backend.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.teacher.backend.entity.CourseCatalogEntry;
import com.teacher.backend.repository.CourseCatalogEntryRepository;
import com.teacher.backend.repository.CourseKnowledgePointPrereqRepository;
import com.teacher.backend.repository.CourseKnowledgePointRepository;
import com.teacher.backend.repository.TeacherCoursePermissionRepository;

@Service
public class CourseCatalogService {

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
    }

    public List<String> allCourses() {
        return courseCatalogEntryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
            .map(CourseCatalogEntry::getCourseName)
            .toList();
    }

    public List<CourseCatalogEntry> allCourseEntries() {
        return courseCatalogEntryRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    public Optional<CourseCatalogEntry> findEntryByCourseName(String rawCourseName) {
        if (!StringUtils.hasText(rawCourseName)) return Optional.empty();
        String normalized = normalizeCourseName(rawCourseName);
        return courseCatalogEntryRepository.findByCourseNameIgnoreCase(normalized);
    }

    /** 课程目录中是否已有该名称（经 {@link #normalizeCourseName} 归一化后比较，忽略大小写）。 */
    public boolean isCourseInCatalog(String rawCourseName) {
        if (!StringUtils.hasText(rawCourseName)) {
            return false;
        }
        String normalized = normalizeCourseName(rawCourseName);
        return courseCatalogEntryRepository.existsByCourseNameIgnoreCase(normalized);
    }

    /** 目录中第一门课；目录为空时返回空串（不注入任何默认课程）。 */
    public String defaultCourse() {
        List<String> courses = allCourses();
        return courses.isEmpty() ? "" : courses.get(0);
    }

    public String normalizeCourseName(String rawCourseName) {
        if (!StringUtils.hasText(rawCourseName)) {
            return "";
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

        // normalized 可能为空串（用户未选课程）；此处不写入目录
        if (!StringUtils.hasText(normalized)) {
            return allCourses();
        }
        if (!courseCatalogEntryRepository.existsByCourseNameIgnoreCase(normalized)) {
            int maxSort = courseCatalogEntryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .mapToInt(CourseCatalogEntry::getSortOrder)
                .max()
                .orElse(-1);
            CourseCatalogEntry e = new CourseCatalogEntry();
            e.setCourseName(normalized);
            e.setSortOrder(maxSort + 1);
            e.setCoverUrl(defaultCoverUrl(normalized));
            e.setSummary(defaultSummary(normalized));
            e.setSyllabus(defaultSyllabus(normalized));
            courseCatalogEntryRepository.save(e);
        }

        return allCourses();
    }

    /**
     * 仅按字面（trim + 忽略大小写判重）写入课程目录，不做别名/子串归一化。
     * <p>审批教师申请时必须使用此方法：{@link #normalizeCourseName} 可能把新课名归并到已有课名，
     * 导致 {@link #addCourse} 跳过插入，而权限表里仍是原课名，从而出现「已通过但广场看不到新课程」。</p>
     */
    public void ensureCatalogContainsExactCourseName(String courseName) {
        if (!StringUtils.hasText(courseName)) {
            return;
        }
        String name = courseName.trim();
        if (courseCatalogEntryRepository.existsByCourseNameIgnoreCase(name)) {
            return;
        }
        int maxSort = courseCatalogEntryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
            .mapToInt(CourseCatalogEntry::getSortOrder)
            .max()
            .orElse(-1);
        CourseCatalogEntry e = new CourseCatalogEntry();
        e.setCourseName(name);
        e.setSortOrder(maxSort + 1);
        e.setCoverUrl(defaultCoverUrl(name));
        e.setSummary(defaultSummary(name));
        e.setSyllabus(defaultSyllabus(name));
        courseCatalogEntryRepository.save(e);
    }

    public CourseCatalogEntry updateCourseMeta(String rawCourseName, String coverUrl, String summary, String syllabus, Long updatedBy) {
        String normalized = normalizeCourseName(rawCourseName);
        CourseCatalogEntry entry = courseCatalogEntryRepository.findByCourseNameIgnoreCase(normalized)
            .orElseThrow(() -> new IllegalArgumentException("课程不存在: " + normalized));
        entry.setCoverUrl(safeTrim(coverUrl));
        entry.setSummary(safeTrim(summary));
        entry.setSyllabus(safeTrim(syllabus));
        entry.setUpdatedBy(updatedBy);
        entry.setUpdatedAt(LocalDateTime.now());
        return courseCatalogEntryRepository.save(entry);
    }

    public String defaultCoverUrl(String courseName) {
        String text = StringUtils.hasText(courseName) ? courseName.trim() : "课程";
        return "https://dummyimage.com/640x360/edf2ff/2b4eff&text=" + java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8);
    }

    public String defaultSummary(String courseName) {
        String name = StringUtils.hasText(courseName) ? courseName.trim() : "本课程";
        return name + "简介：围绕课程核心知识点，强调概念理解、案例分析与实践应用。";
    }

    public String defaultSyllabus(String courseName) {
        String name = StringUtils.hasText(courseName) ? courseName.trim() : "本课程";
        return "1. 课程导论\n2. 核心概念与术语\n3. 方法与流程\n4. 案例分析\n5. 综合实践与复盘\n\n（" + name + " 可按教学需要调整）";
    }

    private String safeTrim(String v) {
        if (!StringUtils.hasText(v)) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
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
