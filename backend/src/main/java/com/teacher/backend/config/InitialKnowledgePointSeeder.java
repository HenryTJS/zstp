package com.teacher.backend.config;

import java.util.List;
import java.util.Map;

import com.teacher.backend.entity.CourseKnowledgePoint;
import com.teacher.backend.repository.CourseKnowledgePointRepository;
import com.teacher.backend.service.CourseCatalogService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "app.seed", name = "knowledge", havingValue = "true", matchIfMissing = false)
public class InitialKnowledgePointSeeder implements CommandLineRunner {

    private final CourseKnowledgePointRepository courseKnowledgePointRepository;
    private final CourseCatalogService courseCatalogService;

    public InitialKnowledgePointSeeder(
        CourseKnowledgePointRepository courseKnowledgePointRepository,
        CourseCatalogService courseCatalogService
    ) {
        this.courseKnowledgePointRepository = courseKnowledgePointRepository;
        this.courseCatalogService = courseCatalogService;
    }

    @Override
    public void run(String... args) {
        for (String course : courseCatalogService.allCourses()) {
            List<Map<String, String>> points = defaultPoints(course);
            int index = 0;
            for (Map<String, String> point : points) {
                if (courseKnowledgePointRepository.findByCourseNameAndPointName(course, point.get("name")).isPresent()) {
                    index++;
                    continue;
                }

                CourseKnowledgePoint entity = new CourseKnowledgePoint();
                entity.setCourseName(course);
                entity.setPointName(point.get("name"));
                entity.setParentPoint(point.get("parent"));
                entity.setSortOrder(index++);
                courseKnowledgePointRepository.save(entity);
            }
        }
    }

    private List<Map<String, String>> defaultPoints(String course) {
        if ("高等数学".equals(course)) {
            return List.of(
                root("函数与极限"),
                child("一元函数微分学", "函数与极限"),
                child("导数与微分", "一元函数微分学"),
                child("微分中值定理", "一元函数微分学"),
                child("极值与最值", "一元函数微分学"),
                child("一元函数积分学", "函数与极限"),
                child("不定积分", "一元函数积分学"),
                child("定积分", "一元函数积分学"),
                child("定积分应用", "定积分"),
                child("多元函数微积分", "函数与极限"),
                child("无穷级数", "函数与极限")
            );
        }

        if ("线性代数与解析几何".equals(course)) {
            return List.of(
                root("行列式"),
                root("矩阵"),
                child("矩阵运算", "矩阵"),
                child("矩阵的秩", "矩阵"),
                child("逆矩阵", "矩阵"),
                child("线性方程组", "矩阵"),
                child("向量组线性相关性", "线性方程组"),
                child("特征值与特征向量", "矩阵"),
                child("相似对角化", "特征值与特征向量"),
                root("解析几何基础"),
                child("平面与直线", "解析几何基础"),
                child("二次曲面", "解析几何基础"),
                child("二次型", "特征值与特征向量")
            );
        }

        if ("概率论与数理统计".equals(course)) {
            return List.of(
                root("随机事件与概率"),
                child("条件概率与独立性", "随机事件与概率"),
                child("随机变量及其分布", "随机事件与概率"),
                child("离散型随机变量", "随机变量及其分布"),
                child("连续型随机变量", "随机变量及其分布"),
                child("二维随机变量", "随机变量及其分布"),
                child("数字特征", "随机变量及其分布"),
                child("大数定律", "数字特征"),
                child("中心极限定理", "数字特征"),
                root("数理统计基础"),
                child("参数估计", "数理统计基础"),
                child("假设检验", "数理统计基础")
            );
        }

        if ("复变函数与积分变换".equals(course)) {
            return List.of(
                root("复数与复平面"),
                child("解析函数", "复数与复平面"),
                child("初等复变函数", "解析函数"),
                child("复积分", "解析函数"),
                child("柯西积分公式", "复积分"),
                child("级数展开", "解析函数"),
                child("洛朗级数", "级数展开"),
                child("留数定理", "级数展开"),
                root("积分变换"),
                child("傅里叶变换", "积分变换"),
                child("拉普拉斯变换", "积分变换")
            );
        }

        return List.of(
            root("课程导论"),
            child("核心概念", "课程导论"),
            child("关键术语", "核心概念"),
            child("基础方法", "课程导论"),
            child("方法步骤", "基础方法"),
            child("常见误区", "基础方法"),
            root("应用实践"),
            child("案例分析", "应用实践"),
            child("综合练习", "应用实践"),
            child("复盘与改进", "综合练习")
        );
    }

    private Map<String, String> root(String name) {
        return Map.of("name", name);
    }

    private Map<String, String> child(String name, String parent) {
        return Map.of(
            "name", name,
            "parent", parent
        );
    }
}
