package com.teacher.backend.config;

import com.teacher.backend.entity.CourseKnowledgePoint;
import com.teacher.backend.entity.CourseKnowledgePointPrereq;
import com.teacher.backend.repository.CourseKnowledgePointPrereqRepository;
import com.teacher.backend.repository.CourseKnowledgePointRepository;
import com.teacher.backend.service.CourseCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.seed", name = "examples", havingValue = "true", matchIfMissing = false)
public class ExampleKnowledgeGraphSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ExampleKnowledgeGraphSeeder.class);

    private final CourseKnowledgePointRepository pointRepo;
    private final CourseKnowledgePointPrereqRepository prereqRepo;
    private final CourseCatalogService courseCatalogService;

    public ExampleKnowledgeGraphSeeder(
        CourseKnowledgePointRepository pointRepo,
        CourseKnowledgePointPrereqRepository prereqRepo,
        CourseCatalogService courseCatalogService
    ) {
        this.pointRepo = pointRepo;
        this.prereqRepo = prereqRepo;
        this.courseCatalogService = courseCatalogService;
    }

    @Override
    public void run(String... args) {
        log.info("ExampleKnowledgeGraphSeeder start: clearing existing knowledge points and prereqs");

        // delete prereqs first to avoid FK issues
        prereqRepo.deleteAll();
        pointRepo.deleteAll();

        // Insert sample hierarchical data for two courses
        seedCourse("高等数学");
        seedCourse("线性代数与解析几何");

        log.info("ExampleKnowledgeGraphSeeder finished seeding example knowledge graph data");
    }

    private void seedCourse(String courseName) {
        String normalized = courseCatalogService.normalizeCourseName(courseName);
        log.info("Seeding course: {}", normalized);

        Map<String, Long> nameToId = new HashMap<>();

        // Chapter 1
        Long chap1 = savePoint(normalized, "第一章：基础概念", null, 0);
        nameToId.put("第一章：基础概念", chap1);

        // Sections under chapter 1
        Long sec11 = savePoint(normalized, "1.1 函数与映射", "第一章：基础概念", 0);
        nameToId.put("1.1 函数与映射", sec11);
        Long sec12 = savePoint(normalized, "1.2 极限与连续", "第一章：基础概念", 1);
        nameToId.put("1.2 极限与连续", sec12);

        // Concrete points under section 1.1
        Long p111 = savePoint(normalized, "函数的定义", "1.1 函数与映射", 0);
        Long p112 = savePoint(normalized, "函数的表示方法", "1.1 函数与映射", 1);
        nameToId.put("函数的定义", p111);
        nameToId.put("函数的表示方法", p112);

        // Concrete points under section 1.2
        Long p121 = savePoint(normalized, "极限的定义", "1.2 极限与连续", 0);
        Long p122 = savePoint(normalized, "极限的运算法则", "1.2 极限与连续", 1);
        nameToId.put("极限的定义", p121);
        nameToId.put("极限的运算法则", p122);

        // Add prereq between sibling points (same parent) - allowed
        // e.g., 极限的定义 -> 极限的运算法则 (both under 1.2)
        addPrereq(normalized, p122, p121);

        // Chapter 2
        Long chap2 = savePoint(normalized, "第二章：导数与微分", null, 2);
        nameToId.put("第二章：导数与微分", chap2);

        Long sec21 = savePoint(normalized, "2.1 导数的概念", "第二章：导数与微分", 0);
        Long sec22 = savePoint(normalized, "2.2 微分应用", "第二章：导数与微分", 1);
        nameToId.put("2.1 导数的概念", sec21);
        nameToId.put("2.2 微分应用", sec22);

        Long p211 = savePoint(normalized, "导数定义", "2.1 导数的概念", 0);
        Long p212 = savePoint(normalized, "求导法则", "2.1 导数的概念", 1);
        addPrereq(normalized, p212, p211);
    }

    private Long savePoint(String courseName, String pointName, String parentPoint, int order) {
        CourseKnowledgePoint p = new CourseKnowledgePoint();
        p.setCourseName(courseName);
        p.setPointName(pointName);
        p.setParentPoint(parentPoint);
        p.setSortOrder(order);
        CourseKnowledgePoint saved = pointRepo.save(p);
        return saved.getId();
    }

    private void addPrereq(String courseName, Long pointId, Long prereqId) {
        if (pointId == null || prereqId == null) return;
        CourseKnowledgePointPrereq pr = new CourseKnowledgePointPrereq();
        pr.setCourseName(courseName);
        pr.setPointId(pointId);
        pr.setPrereqPointId(prereqId);
        prereqRepo.save(pr);
    }
}
