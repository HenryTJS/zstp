package com.teacher.backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.teacher.backend.entity.CourseKnowledgePoint;
import com.teacher.backend.entity.Material;
import com.teacher.backend.repository.CourseKnowledgePointRepository;
import com.teacher.backend.repository.MaterialRepository;
import com.teacher.backend.util.KnowledgePointUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 错题驱动的个性化学习闭环。
 *
 * <p>职责：识别错题知识点 → AI 分析错误原因 → 推荐课程资料 → 生成针对性练习。
 * <p>设计要点：
 * <ul>
 *   <li>错因分析与练习生成合并为<b>一次</b> AI 调用，配合 {@link AiClient} 的结果缓存，
 *       避免多次消耗配额与费用。</li>
 *   <li>资料推荐<b>不依赖 AI</b>，直接查库并沿知识点层级继承，因此即使 AI 不可用仍能给出复习资料。</li>
 *   <li>AI 输出的题目会做一次严格清洗（类型、选项、答案、分值），不合格的题目直接丢弃，
 *       防止脏数据进入前端答题流程。</li>
 * </ul>
 */
@Service
public class WrongDiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(WrongDiagnosisService.class);

    /** 最多送入 AI 的错题数量，用于控制 prompt 长度与 token 消耗。 */
    private static final int MAX_ITEMS_TO_AI = 5;

    /** 生成的针对性练习数量。 */
    private static final int PRACTICE_COUNT = 3;

    /** 选择题允许的最大选项数（清洗 AI 输出用）。 */
    private static final int MAX_OPTIONS = 6;

    /** 推荐资料的数量上限。 */
    private static final int MAX_MATERIALS = 6;

    /** 允许的错误类型，AI 返回其他值时回落到默认值，保证前端展示稳定。 */
    private static final List<String> ERROR_TYPES = List.of("概念不清", "计算错误", "步骤缺失", "前置知识不足");

    private final AiClient aiClient;
    private final MaterialRepository materialRepository;
    private final CourseKnowledgePointRepository courseKnowledgePointRepository;
    private final CourseCatalogService courseCatalogService;
    private final ApiResponseMapper responseMapper;

    public WrongDiagnosisService(
        AiClient aiClient,
        MaterialRepository materialRepository,
        CourseKnowledgePointRepository courseKnowledgePointRepository,
        CourseCatalogService courseCatalogService,
        ApiResponseMapper responseMapper
    ) {
        this.aiClient = aiClient;
        this.materialRepository = materialRepository;
        this.courseKnowledgePointRepository = courseKnowledgePointRepository;
        this.courseCatalogService = courseCatalogService;
        this.responseMapper = responseMapper;
    }

    /**
     * 对某个知识点的错题做诊断，返回：错因分析 + 推荐资料 + 针对性练习。
     *
     * @param courseName     课程名（会做归一化）
     * @param knowledgePoint 知识点名
     * @param wrongItems     错题条目列表，可为空
     */
    public Map<String, Object> diagnose(String courseName, String knowledgePoint, List<Map<String, Object>> wrongItems) {
        String cn = courseCatalogService.normalizeCourseName(courseName);
        String kp = safe(knowledgePoint);
        List<Map<String, Object>> items = wrongItems == null ? List.of() : wrongItems;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("courseName", cn);
        out.put("knowledgePoint", kp);
        out.put("wrongCount", items.size());
        out.put("accuracyBefore", computeAccuracy(items));
        out.put("recommendedMaterials", recommendMaterials(cn, kp));
        out.put("aiEnabled", aiClient.aiEnabled());

        if (items.isEmpty()) {
            out.put("diagnosisSource", "none");
            out.put("diagnosis", fallbackDiagnosis(items, "错题本中该知识点暂无错题，无法进行错因分析。"));
            out.put("practiceQuestions", List.of());
            return out;
        }

        Map<String, Object> ai = callAi(cn, kp, items);
        if (ai == null) {
            out.put("diagnosisSource", "fallback");
            out.put("diagnosis", fallbackDiagnosis(items, null));
            out.put("practiceQuestions", List.of());
            return out;
        }

        out.put("diagnosisSource", "ai");
        out.put("diagnosis", ai.get("diagnosis"));
        out.put("practiceQuestions", ai.get("questions"));
        return out;
    }

    /**
     * 计算干预前的得分率（0~1）。返回 null 表示错题里没有可用的分值信息。
     */
    private Double computeAccuracy(List<Map<String, Object>> items) {
        double score = 0;
        double full = 0;
        for (Map<String, Object> item : items) {
            score += toDouble(item.get("score"));
            full += toDouble(item.get("fullScore"));
        }
        if (full <= 0) {
            return null;
        }
        return Math.round(score / full * 1000.0) / 1000.0;
    }

    /**
     * 推荐该知识点（含父级与子级）下的课程资料。精确匹配知识点的资料优先展示。
     */
    private List<Map<String, Object>> recommendMaterials(String courseName, String knowledgePoint) {
        if (!StringUtils.hasText(courseName) || !StringUtils.hasText(knowledgePoint)) {
            return List.of();
        }
        try {
            List<CourseKnowledgePoint> allPoints =
                courseKnowledgePointRepository.findByCourseNameOrderBySortOrderAscIdAsc(courseName);

            List<String> queryPoints = new ArrayList<>(KnowledgePointUtils.getAllAncestors(knowledgePoint, allPoints));
            queryPoints.addAll(KnowledgePointUtils.getAllDescendants(knowledgePoint, allPoints));
            List<String> distinctPoints = queryPoints.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
            if (distinctPoints.isEmpty()) {
                return List.of();
            }

            return materialRepository.findByCourseNameAndKnowledgePointIn(courseName, distinctPoints).stream()
                .sorted(Comparator
                    .comparingInt((Material material) -> knowledgePoint.equals(safe(material.getKnowledgePoint())) ? 0 : 1)
                    .thenComparing(Material::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_MATERIALS)
                .map(responseMapper::toMaterialMap)
                .toList();
        } catch (Exception exception) {
            log.warn("recommendMaterials failed: {}", exception.getMessage());
            return List.of();
        }
    }

    /**
     * 一次 AI 调用同时拿到错因分析与针对性练习；失败时返回 null 由调用方降级。
     */
    private Map<String, Object> callAi(String courseName, String knowledgePoint, List<Map<String, Object>> items) {
        if (!aiClient.aiEnabled()) {
            return null;
        }

        String systemPrompt = "你是学科教学诊断专家。你会收到学生的错题（含学生答案与正确答案），"
            + "需要判断错误原因，并生成针对性练习。"
            + "输出 JSON:{errorType:string,reason:string,misconception:string,reviewPoints:[string],"
            + "questions:[{question_type:string,question:string,options:[string],answer:string,"
            + "explanation:string,fullScore:number,focusPointName:string}]}。"
            + "要求："
            + "1) errorType 只能取以下之一：概念不清、计算错误、步骤缺失、前置知识不足；"
            + "2) reason 说明判断依据，80~150 字；misconception 指出学生的具体误解点，50~100 字；"
            + "3) reviewPoints 给出 2~4 条需要复习的要点，每条不超过 20 字；"
            + "4) questions 生成 " + PRACTICE_COUNT + " 道针对性练习，question_type 只能是 选择题 或 填空题；"
            + "5) 选择题的 options 必须是 4 个选项，形如 [\"A. ...\",\"B. ...\",\"C. ...\",\"D. ...\"]；"
            + "填空题的 options 输出空数组；"
            + "6) 选择题的 answer 只填字母（如 \"B\"），填空题的 answer 填参考答案文本；"
            + "7) fullScore 取 10；每题都要有 explanation 解析；focusPointName 填知识点名称；"
            + "8) 练习必须针对学生暴露的具体错误，不要出与错题无关的题目。";

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("课程：").append(courseName).append('\n');
        userPrompt.append("知识点：").append(knowledgePoint).append('\n');
        userPrompt.append("学生错题：\n");

        int index = 1;
        for (Map<String, Object> item : items.stream().limit(MAX_ITEMS_TO_AI).toList()) {
            userPrompt.append(index++).append(". 题干：").append(safe(item.get("question"))).append('\n');

            String options = formatOptions(item.get("options"));
            if (StringUtils.hasText(options)) {
                userPrompt.append("   选项：").append(options).append('\n');
            }

            userPrompt.append("   学生答案：")
                .append(firstNonBlank(item.get("myAnswer"), item.get("studentAnswerRaw")))
                .append('\n');
            userPrompt.append("   正确答案：")
                .append(firstNonBlank(item.get("answer"), item.get("referenceAnswerRaw")))
                .append('\n');

            String explanation = safe(item.get("explanation"));
            if (StringUtils.hasText(explanation)) {
                userPrompt.append("   解析：").append(explanation).append('\n');
            }
        }

        try {
            Map<String, Object> parsed = aiClient.chatJson(systemPrompt, userPrompt.toString());
            if (parsed == null || parsed.isEmpty()) {
                return null;
            }

            String errorType = safe(parsed.get("errorType"));
            Map<String, Object> diagnosis = new LinkedHashMap<>();
            diagnosis.put("errorType", ERROR_TYPES.contains(errorType) ? errorType : ERROR_TYPES.get(0));
            diagnosis.put("reason", safe(parsed.get("reason")));
            diagnosis.put("misconception", safe(parsed.get("misconception")));
            diagnosis.put("reviewPoints", toStringList(parsed.get("reviewPoints")));

            List<Map<String, Object>> questions = normalizeQuestions(parsed.get("questions"), knowledgePoint);
            if (!StringUtils.hasText(String.valueOf(diagnosis.get("reason"))) && questions.isEmpty()) {
                log.warn("wrong-diagnosis: AI 返回内容不可用，降级为兜底诊断");
                return null;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("diagnosis", diagnosis);
            result.put("questions", questions);
            return result;
        } catch (Exception exception) {
            log.warn("wrong-diagnosis AI call failed: {}", exception.getMessage());
            return null;
        }
    }

    /**
     * 清洗 AI 返回的题目：类型非法则回落、选择题必须有选项、答案必须非空，否则丢弃该题。
     */
    private List<Map<String, Object>> normalizeQuestions(Object raw, String knowledgePoint) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Object node : list) {
            if (out.size() >= PRACTICE_COUNT) {
                break;
            }
            if (!(node instanceof Map<?, ?> map)) {
                continue;
            }

            String question = safe(map.get("question"));
            if (!StringUtils.hasText(question)) {
                continue;
            }

            String type = safe(map.get("question_type"));
            if (!"选择题".equals(type) && !"填空题".equals(type)) {
                type = "选择题";
            }

            List<String> options = toStringList(map.get("options"));
            if ("选择题".equals(type)) {
                if (options.size() < 2) {
                    continue;
                }
                if (options.size() > MAX_OPTIONS) {
                    options = options.subList(0, MAX_OPTIONS);
                }
            } else {
                options = List.of();
            }

            String answer = safe(map.get("answer"));
            if (!StringUtils.hasText(answer)) {
                continue;
            }

            String focusPointName = safe(map.get("focusPointName"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("question_type", type);
            item.put("question", question);
            item.put("options", options);
            item.put("answer", answer);
            item.put("explanation", safe(map.get("explanation")));
            item.put("fullScore", normalizeFullScore(map.get("fullScore")));
            item.put("focusPointName", StringUtils.hasText(focusPointName) ? focusPointName : knowledgePoint);
            out.add(item);
        }
        return out;
    }

    /**
     * AI 不可用（或关闭）时的兜底诊断，保证前端仍有可展示内容。
     */
    private Map<String, Object> fallbackDiagnosis(List<Map<String, Object>> items, String reasonOverride) {
        Map<String, Object> diagnosis = new LinkedHashMap<>();
        diagnosis.put("errorType", ERROR_TYPES.get(0));
        diagnosis.put("reason", StringUtils.hasText(reasonOverride)
            ? reasonOverride
            : "AI 诊断暂时不可用。已根据错题本记录给出基础提示：建议先重做错题并逐题对照解析，"
                + "确认自己卡在概念理解、计算过程还是审题环节。");
        diagnosis.put("misconception", "当前无法自动定位具体误解点，请结合错题解析手动复盘。");

        List<String> points = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String kp = safe(item.get("knowledgePoint"));
            if (StringUtils.hasText(kp) && !points.contains(kp)) {
                points.add(kp);
            }
            if (points.size() >= 3) {
                break;
            }
        }
        if (points.isEmpty()) {
            points.add("回顾该知识点的基本定义");
        }
        diagnosis.put("reviewPoints", points);
        return diagnosis;
    }

    private static int normalizeFullScore(Object raw) {
        double value = toDouble(raw);
        if (value <= 0) {
            return 10;
        }
        return (int) Math.min(100, Math.max(1, Math.round(value)));
    }

    private static String formatOptions(Object raw) {
        List<String> options = toStringList(raw);
        return options.isEmpty() ? "" : String.join(" / ", options);
    }

    private static List<String> toStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object node : list) {
            if (node == null) {
                continue;
            }
            String text = String.valueOf(node).trim();
            if (!text.isEmpty()) {
                out.add(text);
            }
        }
        return out;
    }

    private static String firstNonBlank(Object primary, Object fallback) {
        String first = safe(primary);
        return StringUtils.hasText(first) ? first : safe(fallback);
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
