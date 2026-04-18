package com.teacher.backend.config;

import com.teacher.backend.entity.CourseKnowledgePoint;
import com.teacher.backend.repository.CourseKnowledgePointRepository;
import com.teacher.backend.service.CourseCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.seed", name = "examples", havingValue = "true", matchIfMissing = false)
public class ExampleKnowledgeGraphSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ExampleKnowledgeGraphSeeder.class);

    private final CourseKnowledgePointRepository pointRepo;
    private final CourseCatalogService courseCatalogService;

    public ExampleKnowledgeGraphSeeder(
        CourseKnowledgePointRepository pointRepo,
        CourseCatalogService courseCatalogService
    ) {
        this.pointRepo = pointRepo;
        this.courseCatalogService = courseCatalogService;
    }

    @Override
    public void run(String... args) {
        log.info("ExampleKnowledgeGraphSeeder start: clearing existing knowledge points");

        pointRepo.deleteAll();

        seedCourse("示例课程A");
        seedCourse("示例课程B");

        log.info("ExampleKnowledgeGraphSeeder finished seeding example knowledge graph data");
    }

    private void seedCourse(String courseName) {
        String normalized = courseCatalogService.normalizeCourseName(courseName);
        log.info("Seeding course: {}", normalized);

        savePoint(normalized, "第一章：课程导论", null, 0);

        savePoint(normalized, "1.1 核心概念", "第一章：课程导论", 0);
        savePoint(normalized, "1.2 基础方法", "第一章：课程导论", 1);

        savePoint(normalized, "概念定义", "1.1 核心概念", 0);
        savePoint(normalized, "术语辨析", "1.1 核心概念", 1);

        savePoint(normalized, "方法步骤", "1.2 基础方法", 0);
        savePoint(normalized, "常见误区", "1.2 基础方法", 1);

        savePoint(normalized, "第二章：应用与实践", null, 2);

        savePoint(normalized, "2.1 案例分析", "第二章：应用与实践", 0);
        savePoint(normalized, "2.2 综合实践", "第二章：应用与实践", 1);

        savePoint(normalized, "案例拆解", "2.1 案例分析", 0);
        savePoint(normalized, "方案评估", "2.1 案例分析", 1);
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
}
