package com.teacher.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.teacher.backend.entity.CourseKnowledgePoint;
import com.teacher.backend.repository.CourseKnowledgePointRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Service for AI-powered suggestions, knowledge graph, and relevance analysis.
 */
@Service
public class AiSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AiSuggestionService.class);

    private final AiClient aiClient;
    private final CourseKnowledgePointRepository courseKnowledgePointRepository;
    private final CourseCatalogService courseCatalogService;

    public AiSuggestionService(
        AiClient aiClient,
        CourseKnowledgePointRepository courseKnowledgePointRepository,
        CourseCatalogService courseCatalogService
    ) {
        this.aiClient = aiClient;
        this.courseKnowledgePointRepository = courseKnowledgePointRepository;
        this.courseCatalogService = courseCatalogService;
    }

    /**
     * Generate a knowledge graph from course knowledge points.
     */
    public Map<String, Object> generateKnowledgeGraph(String topic) {
        String courseName = courseCatalogService.normalizeCourseName(topic);
        List<CourseKnowledgePoint> points = courseKnowledgePointRepository.findByCourseNameOrderBySortOrderAscIdAsc(courseName);

        Map<String, String> idMap = new LinkedHashMap<>();
        Map<Long, String> idByDbId = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        nodes.add(node("root", courseName, "课程"));
        idMap.put(courseName, "root");

        for (CourseKnowledgePoint point : points) {
            if (courseName.equals(point.getPointName()) && !StringUtils.hasText(point.getParentPoint())) {
                idByDbId.put(point.getId(), "root");
                continue;
            }
            String pointId = "p" + point.getId();
            idMap.put(point.getPointName(), pointId);
            idByDbId.put(point.getId(), pointId);
            nodes.add(node(pointId, point.getPointName(), "知识点"));
        }

        Set<String> knownPoints = idMap.keySet();
        for (CourseKnowledgePoint point : points) {
            if (courseName.equals(point.getPointName()) && !StringUtils.hasText(point.getParentPoint())) {
                continue;
            }
            String pointId = idMap.get(point.getPointName());
            String parentPoint = point.getParentPoint();
            if (StringUtils.hasText(parentPoint) && knownPoints.contains(parentPoint)) {
                edges.add(edge(idMap.get(parentPoint), pointId, "包含"));
            } else {
                edges.add(edge("root", pointId, "包含"));
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("courseName", courseName);
        response.put("title", courseName + "知识图谱");
        response.put("nodes", nodes);
        response.put("edges", edges);
        response.put("suggestions", List.of(
            "先梳理一级知识点，再进入分支知识点练习。",
            "按章节树由浅入深，先掌握父节点再学叶子知识点。",
            "将薄弱知识点加入错题复盘清单。"
        ));
        return response;
    }

    /**
     * Generate learning suggestions for a knowledge point.
     */
    public Map<String, Object> generateLearningSuggestions(String topic, String knowledgePoint) {
        String courseName = courseCatalogService.normalizeCourseName(topic);
        String kp = safe(knowledgePoint);
        if (!StringUtils.hasText(kp)) {
            kp = "该知识点";
        }

        boolean enabled = aiClient.aiEnabled();
        if (enabled) {
            log.info("generateLearningSuggestions: aiEnabled=true, courseName={}, knowledgePoint={}", courseName, kp);
            String systemPrompt = "你是学习规划助手。根据课程名称与某知识点，生成 3 条学习建议。输出 JSON:{suggestions:[string,string,string]}。";
            String userPrompt = "课程：" + courseName + "\n知识点：" + kp + "\n要求：每条建议不超过25字，尽量可操作（例如：复习、练习、错题复盘、总结）。";
            try {
                Map<String, Object> parsed = aiClient.chatJson(systemPrompt, userPrompt);
                Object suggestionsObj = parsed == null ? null : parsed.get("suggestions");
                if (suggestionsObj instanceof List<?> list) {
                    List<String> out = new ArrayList<>();
                    for (Object item : list) {
                        if (item == null) continue;
                        String s = String.valueOf(item).trim();
                        if (!s.isBlank()) out.add(s);
                    }
                    if (!out.isEmpty()) {
                        while (out.size() < 3) out.add(fallbackSuggestion(kp, out.size()));
                        if (out.size() > 3) out = out.subList(0, 3);
                        return Map.of("suggestions", out);
                    }
                }
                log.warn("generateLearningSuggestions: AI returned invalid suggestions. suggestionsObjType={}, parsedKeys={}",
                    suggestionsObj == null ? "null" : suggestionsObj.getClass().getName(),
                    parsed == null ? "null" : parsed.keySet());
            } catch (Exception ex) {
                log.warn("generateLearningSuggestions: AI call failed: {}", ex.getMessage());
            }
        }

        if (!enabled) {
            log.warn("generateLearningSuggestions: aiEnabled=false -> fallback. courseName={}, knowledgePoint={}", courseName, kp);
        }
        return Map.of("suggestions", List.of(
            fallbackSuggestion(kp, 0),
            fallbackSuggestion(kp, 1),
            fallbackSuggestion(kp, 2)
        ));
    }

    /**
     * Generate major relevance analysis.
     */
    public Map<String, Object> generateMajorRelevance(String topic, String knowledgePoint, String major) {
        String courseName = courseCatalogService.normalizeCourseName(topic);
        String kp = safe(knowledgePoint);
        String mj = safe(major);
        if (!StringUtils.hasText(kp)) {
            kp = "该知识点";
        }
        if (!StringUtils.hasText(mj)) {
            mj = "未设置专业";
        }

        if (aiClient.aiEnabled()) {
            String systemPrompt = "你是教学顾问，需要判断「知识点」和「专业方向」的关联度。"
                + "必须输出 JSON，格式为："
                + "{score_level:number, summary:string, related_contents:[string], low_relevance_reason:string}。"
                + "约束："
                + "1) score_level 只能是 1~5 的整数（5 最高，1 最低）；"
                + "2) summary 用 1~2 句中文解释评分依据；"
                + "3) related_contents 列出与该专业较相关的课程/能力/场景要点（0~4 条）；"
                + "4) 若关联度较低（score_level<=2），不要硬凑相关性，low_relevance_reason 说明为什么关联弱；否则 low_relevance_reason 置空字符串。";
            String userPrompt = "课程：" + courseName
                + "\n知识点：" + kp
                + "\n专业：" + mj
                + "\n请严格按 JSON 输出，不要输出额外文本。";
            try {
                Map<String, Object> parsed = aiClient.chatJson(systemPrompt, userPrompt);
                int level = clampMajorRelevanceLevel(parsed.get("score_level"));
                String summary = safe(String.valueOf(parsed.getOrDefault("summary", "")));
                if (!StringUtils.hasText(summary)) {
                    summary = "可结合专业课程目标评估该知识点的应用价值。";
                }
                List<String> related = sanitizeStringList(parsed.get("related_contents"), 4);
                String lowReason = safe(String.valueOf(parsed.getOrDefault("low_relevance_reason", "")));
                if (level <= 2 && !StringUtils.hasText(lowReason)) {
                    lowReason = "该知识点在该专业核心课程中出现频率较低，更多属于通用基础能力。";
                }
                if (level >= 3) {
                    lowReason = "";
                }
                return Map.of(
                    "scoreLevel", level,
                    "summary", summary,
                    "relatedContents", related,
                    "lowRelevanceReason", lowReason
                );
            } catch (Exception ex) {
                log.warn("generateMajorRelevance: AI call failed: {}", ex.getMessage());
            }
        }

        int fallbackLevel = 3;
        return Map.of(
            "scoreLevel", fallbackLevel,
            "summary", "该知识点与专业学习存在一定通用关联，建议结合具体课程任务再细化判断。",
            "relatedContents", List.of("基础定量分析能力", "问题建模与逻辑推理"),
            "lowRelevanceReason", ""
        );
    }

    /**
     * Generate teaching suggestions based on published test aggregated data.
     */
    public String publishedTestTeachingSuggestions(String aggregatedContext, String teacherUsername) {
        String ctx = safe(aggregatedContext);
        if (!StringUtils.hasText(ctx)) {
            return "暂无可用统计数据，无法生成建议。";
        }
        if (!aiClient.aiEnabled()) {
            return "当前未配置 AI（OPENAI_API_KEY 为空）。建议结合各题得分率，对得分率最低的题目先做共性错因讲评，再布置同知识点变式训练，并关注仍未提交的学生。";
        }
        String who = safe(teacherUsername);
        String systemPrompt = """
            你是资深学科教研顾问，只用简体中文回答。
            你将收到一次「知识点随堂测验」的结构化统计数据摘要。请基于真实数据给出可执行的教学建议，不要编造摘要中未出现的题号或比例。
            输出要求：
            1) 用二级标题「整体判断」「课堂讲评」「分层巩固」「后续衔接」四段组织（Markdown 可用 ## 标题）；
            2) 每段 2～4 条要点，动词开头、可操作；
            3) 总字数控制在 500 字以内；
            4) 若提交人数很少或为零，需提醒样本偏差，并给出动员与补测建议。
            """;
        String userPrompt = (StringUtils.hasText(who) ? ("教师用户名：" + who + "\n") : "")
            + "测验数据摘要如下：\n"
            + ctx
            + "\n请直接输出建议正文。";
        try {
            String out = aiClient.chatText(systemPrompt, userPrompt);
            return StringUtils.hasText(out) ? out.trim() : "暂时没有可用建议，请稍后重试。";
        } catch (Exception exception) {
            log.warn("publishedTestTeachingSuggestions failed: {}", exception.getMessage());
            return "生成教学建议时 AI 服务异常，请稍后重试。";
        }
    }

    // --- Private helpers ---

    private String fallbackSuggestion(String knowledgePoint, int idx) {
        return switch (idx) {
            case 0 -> "先梳理「" + knowledgePoint + "」的核心概念与常见定义。";
            case 1 -> "再针对「" + knowledgePoint + "」做 1-2 道典型练习，记录解题关键。";
            default -> "最后做错题复盘与总结，形成可复用的步骤清单。";
        };
    }

    private int clampMajorRelevanceLevel(Object levelValue) {
        int n = 3;
        if (levelValue instanceof Number number) {
            n = (int) Math.round(number.doubleValue());
        } else {
            try {
                n = (int) Math.round(Double.parseDouble(String.valueOf(levelValue)));
            } catch (Exception ignored) {
                n = 3;
            }
        }
        return Math.max(1, Math.min(5, n));
    }

    private List<String> sanitizeStringList(Object raw, int maxSize) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item == null) continue;
            String s = String.valueOf(item).trim();
            if (s.isBlank()) continue;
            out.add(s);
            if (out.size() >= Math.max(1, maxSize)) break;
        }
        return out;
    }

    private Map<String, Object> node(String id, String label, String group) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("label", label);
        node.put("group", group);
        return node;
    }

    private Map<String, Object> edge(String source, String target, String label) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("source", source);
        edge.put("target", target);
        edge.put("label", label);
        return edge;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
