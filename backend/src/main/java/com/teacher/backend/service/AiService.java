package com.teacher.backend.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PreDestroy;
import com.teacher.backend.dto.GenerateQuestionRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teacher.backend.entity.CourseKnowledgePoint;
import com.teacher.backend.repository.CourseKnowledgePointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final CourseKnowledgePointRepository courseKnowledgePointRepository;
    private final CourseCatalogService courseCatalogService;
    private final com.teacher.backend.repository.GeneratedExamRepository generatedExamRepository;
    // Used for small-batch parallel question generation (e.g. 1-2 questions per request).
    private final ExecutorService questionExecutor = Executors.newFixedThreadPool(4);

    public AiService(
        CourseKnowledgePointRepository courseKnowledgePointRepository,
        CourseCatalogService courseCatalogService,
        com.teacher.backend.repository.GeneratedExamRepository generatedExamRepository,
        @Value("${OPENAI_API_KEY:}") String apiKey,
        @Value("${OPENAI_BASE_URL:https://api.openai.com/v1}") String baseUrl,
        @Value("${OPENAI_MODEL:gpt-4o-mini}") String model
    ) {
        this.courseKnowledgePointRepository = courseKnowledgePointRepository;
        this.courseCatalogService = courseCatalogService;
        this.generatedExamRepository = generatedExamRepository;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = (baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl.trim()).replaceAll("/$", "");
        this.model = model == null || model.isBlank() ? "gpt-4o-mini" : model.trim();

        if (aiEnabled()) {
            log.info("AI enabled. baseUrl={}, model={}, apiKey={}", this.baseUrl, this.model, maskKey(this.apiKey));
        } else {
            log.warn("AI disabled because OPENAI_API_KEY is empty. Fallback templates will be used.");
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            questionExecutor.shutdown();
        } catch (Exception ignored) {
        }
    }

    /**
     * Generate and persist Markdown (original and answer) for an existing GeneratedExam.
     * This method creates Markdown text for paper and answer versions.
     */
    public Map<String, Object> renderSavedExamDocs(Long examId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mdPaper", false);
        out.put("mdAnswer", false);
        try {
            if (examId == null) {
                out.put("error", "missing-id");
                return out;
            }
            if (this.generatedExamRepository == null) {
                out.put("error", "repository-not-available");
                return out;
            }
            var maybe = this.generatedExamRepository.findById(examId);
            if (maybe.isEmpty()) {
                out.put("error", "not-found");
                return out;
            }
            var ge = maybe.get();
            // parse questions (ensure non-null list)
            List<Map<String, Object>> questions;
            try {
                if (ge.getQuestionsJson() != null) {
                    questions = objectMapper.readValue(ge.getQuestionsJson(), new TypeReference<List<Map<String, Object>>>(){});
                    if (questions == null) questions = List.of();
                } else {
                    questions = List.of();
                }
            } catch (Exception ex) {
                log.warn("Failed to parse stored questionsJson for exam {}: {}", examId, ex.getMessage());
                questions = List.of();
            }

            boolean updated = false;
            try {
                byte[] mdOrig = createMarkdownFromQuestions(ge.getTitle(), questions, false);
                if (mdOrig != null) {
                    ge.setMdOriginal(new String(mdOrig, java.nio.charset.StandardCharsets.UTF_8));
                    out.put("mdPaper", true);
                    updated = true;
                }
                byte[] mdAns = createMarkdownFromQuestions(ge.getTitle(), java.util.Objects.requireNonNullElse(questions, List.of()), true);
                if (mdAns != null) {
                    ge.setMdAnswer(new String(mdAns, java.nio.charset.StandardCharsets.UTF_8));
                    out.put("mdAnswer", true);
                    updated = true;
                }
            } catch (Exception ex) {
                log.debug("Markdown generation failed for exam {}: {}", examId, ex.getMessage());
            }

            if (updated) {
                // Ensure non-null for null-safety checkers
                this.generatedExamRepository.save(java.util.Objects.requireNonNull(ge));
            }
        } catch (Exception ex) {
            log.warn("renderSavedExamDocs error: {}", ex.getMessage());
            out.put("error", "exception");
        }
        return out;
    }

    /**
     * Persist an exam from frontend-provided questions and generate Markdown docs.
     * The input schema is the same as /api/generate-question(s).
     */
    public Map<String, Object> saveExamFromQuestions(String title, Integer durationMinutes, List<Map<String, Object>> questions) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("examId", null);
        response.put("mdOriginalPresent", false);
        response.put("mdAnswerPresent", false);

        if (this.generatedExamRepository == null) {
            response.put("message", "repository-not-available");
            return response;
        }

        String resolvedTitle = (title == null || title.isBlank()) ? "试卷" : title.trim();
        int resolvedDuration = durationMinutes == null ? 60 : Math.max(10, Math.min(240, durationMinutes));
        List<Map<String, Object>> src = questions == null ? List.of() : questions;

        // Normalize to markdown-friendly schema: stem/options/answer/analysis/score
        List<Map<String, Object>> normalized = new ArrayList<>();
        int idx = 1;
        for (Map<String, Object> q : src) {
            if (q == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            String qt = String.valueOf(q.getOrDefault("question_type", q.getOrDefault("type", "解答题")));
            Object question = q.getOrDefault("question", q.get("stem"));
            Object explanation = q.getOrDefault("explanation", q.get("analysis"));
            Object answer = q.get("answer");
            Object options = q.get("options");

            m.put("id", q.getOrDefault("id", "Q" + idx));
            m.put("type", qt);
            m.put("stem", question == null ? "" : String.valueOf(question));
            m.put("options", options instanceof List<?> ? options : List.of());
            m.put("answer", answer == null ? "" : String.valueOf(answer));
            m.put("analysis", explanation == null ? "" : String.valueOf(explanation));
            m.put("score", resolvePerQuestionScore(q, qt));
            // keep knowledge_points if present (not used by markdown but useful for debugging)
            if (q.containsKey("knowledge_points")) {
                m.put("knowledge_points", q.get("knowledge_points"));
            }
            normalized.add(m);
            idx++;
            if (idx > 50) break; // hard safety cap
        }

        try {
            com.teacher.backend.entity.GeneratedExam ge = new com.teacher.backend.entity.GeneratedExam();
            ge.setTitle(resolvedTitle);
            ge.setDurationMinutes(resolvedDuration);
            try {
                ge.setQuestionsJson(objectMapper.writeValueAsString(src));
            } catch (Exception ex) {
                ge.setQuestionsJson("[]");
            }
            ge.setPdfOriginal(null);
            ge.setPdfAnswer(null);

            try {
                byte[] mdOrig = createMarkdownFromQuestions(ge.getTitle(), normalized, false);
                if (mdOrig != null) ge.setMdOriginal(new String(mdOrig, java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception ex) {
                log.debug("saveExamFromQuestions: createMarkdownFromQuestions(original) failed: {}", ex.getMessage());
            }
            try {
                byte[] mdAns = createMarkdownFromQuestions(ge.getTitle(), normalized, true);
                if (mdAns != null) ge.setMdAnswer(new String(mdAns, java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception ex) {
                log.debug("saveExamFromQuestions: createMarkdownFromQuestions(answer) failed: {}", ex.getMessage());
            }

            com.teacher.backend.entity.GeneratedExam saved = this.generatedExamRepository.save(ge);
            response.put("examId", saved.getId());
            response.put("mdOriginalPresent", saved.getMdOriginal() != null);
            response.put("mdAnswerPresent", saved.getMdAnswer() != null);
            response.put("message", "saved");
            return response;
        } catch (Exception ex) {
            log.warn("saveExamFromQuestions: persist failed: {}", ex.getMessage());
            response.put("message", "persist-failed");
            return response;
        }
    }

    /**
     * 前端可在题目上带 fullScore（1–100）；否则按题型猜默认分值（兼容旧组卷/测试）。
     */
    private int resolvePerQuestionScore(Map<String, Object> q, String questionType) {
        int fallback = guessScoreByType(questionType);
        if (q == null) {
            return fallback;
        }
        Object fs = q.get("fullScore");
        if (fs == null) {
            fs = q.get("perScore");
        }
        if (fs == null) {
            return fallback;
        }
        try {
            double raw;
            if (fs instanceof Number number) {
                raw = number.doubleValue();
            } else {
                raw = Double.parseDouble(String.valueOf(fs).trim());
            }
            int v = (int) Math.round(raw);
            return Math.max(1, Math.min(100, v));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private int guessScoreByType(String questionType) {
        String t = safe(questionType);
        return switch (t) {
            // 学生端“测试/组卷”里对单选/填空/解答分值有固定要求
            // - 单选：6 分
            // - 填空：6 分
            // - 解答：16 分
            case "选择题", "单选题" -> 6;
            case "多选题" -> 6;
            case "判断题" -> 3;
            case "填空题" -> 6;
            case "简答题" -> 8;
            default -> 16; // 解答题
        };
    }

    private byte[] createMarkdownFromQuestions(String title, List<Map<String, Object>> questions, boolean includeAnswers) throws Exception {
        if (questions == null) questions = List.of();
        StringBuilder md = new StringBuilder();
        md.append("# ").append(title == null ? "试卷" : title).append("\n\n");
        md.append("**题目数量:** ").append(questions.size()).append("\n\n");

        int idx = 1;
        for (Map<String, Object> q : questions) {
            md.append("## 题 ").append(idx++).append("（").append(q.getOrDefault("score", "")).append(" 分）\n\n");
            md.append(String.valueOf(q.getOrDefault("stem", ""))).append("\n\n");

            @SuppressWarnings("unchecked") java.util.List<String> opts = (java.util.List<String>) q.getOrDefault("options", java.util.List.of());
            if (opts != null && !opts.isEmpty()) {
                for (String o : opts) {
                    md.append("- ").append(o).append("\n");
                }
                md.append("\n");
            }

            if (includeAnswers) {
                md.append("**参考答案:** ").append(String.valueOf(q.getOrDefault("answer", ""))).append("\n\n");
                md.append("**解析:** ").append(String.valueOf(q.getOrDefault("analysis", ""))).append("\n\n");
            }
        }

        return md.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
 

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
            // 数据库中的课程根知识点（与课程同名、无父）与图谱 root 节点合并，避免重复
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

    public Map<String, Object> generateLearningSuggestions(String topic, String knowledgePoint) {
        String courseName = courseCatalogService.normalizeCourseName(topic);
        String kp = safe(knowledgePoint);
        if (!org.springframework.util.StringUtils.hasText(kp)) {
            kp = "该知识点";
        }

        boolean enabled = aiEnabled();
        // AI 优先：根据课程与知识点生成“3条、短句、可执行”的学习建议
        if (enabled) {
            log.info("generateLearningSuggestions: aiEnabled=true, courseName={}, knowledgePoint={}", courseName, kp);
            String systemPrompt = "你是学习规划助手。根据课程名称与某知识点，生成 3 条学习建议。输出 JSON:{suggestions:[string,string,string]}。";
            String userPrompt = "课程：" + courseName + "\n知识点：" + kp + "\n要求：每条建议不超过25字，尽量可操作（例如：复习、练习、错题复盘、总结）。";
            try {
                Map<String, Object> parsed = chatJson(systemPrompt, userPrompt);
                Object suggestionsObj = parsed == null ? null : parsed.get("suggestions");
                if (suggestionsObj instanceof List<?> list) {
                    List<String> out = new ArrayList<>();
                    for (Object item : list) {
                        if (item == null) continue;
                        String s = String.valueOf(item).trim();
                        if (!s.isBlank()) out.add(s);
                    }
                    if (!out.isEmpty()) {
                        // 如果 AI 返回了多条，截取前三；如果不足，补兜底
                        while (out.size() < 3) out.add(fallbackSuggestion(kp, out.size()));
                        if (out.size() > 3) out = out.subList(0, 3);
                        return Map.of("suggestions", out);
                    }
                }

                // AI 返回但不符合预期格式时，这里把原因记录出来方便定位
                log.warn("generateLearningSuggestions: AI returned invalid suggestions. suggestionsObjType={}, parsedKeys={}",
                    suggestionsObj == null ? "null" : suggestionsObj.getClass().getName(),
                    parsed == null ? "null" : parsed.keySet());
            } catch (Exception ex) {
                log.warn("generateLearningSuggestions: AI call failed: {}", ex.getMessage());
            }
        }

        // fallback：无需 AI 也能给出 3 条固定但带知识点上下文的建议
        if (!enabled) {
            log.warn("generateLearningSuggestions: aiEnabled=false -> fallback. courseName={}, knowledgePoint={}", courseName, kp);
        }
        return Map.of("suggestions", List.of(
            fallbackSuggestion(kp, 0),
            fallbackSuggestion(kp, 1),
            fallbackSuggestion(kp, 2)
        ));
    }

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

        if (aiEnabled()) {
            String systemPrompt = "你是教学顾问，需要判断“知识点”和“专业方向”的关联度。"
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
                Map<String, Object> parsed = chatJson(systemPrompt, userPrompt);
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

    private String fallbackSuggestion(String knowledgePoint, int idx) {
        // 用“该知识点”做兜底时也能正常描述
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

    public Map<String, Object> generateQuestion(String topic, String difficulty, String questionType) {
        String resolvedTopic = StringUtils.hasText(topic) ? topic.trim() : "通用知识点";
        String resolvedDifficulty = StringUtils.hasText(difficulty) ? difficulty.trim() : "中等";
        String resolvedQuestionType = normalizeQuestionType(questionType);
        boolean usedFallback = false;
        String fallbackReason = "";

        if (aiEnabled()) {
            String systemPrompt = "你是教学命题助手。仅支持题型: 选择题/多选题/判断题/填空题/解答题（其中选择题=单选题）。输出 JSON:{question:string, question_type:string, options:[string], answer:string, explanation:string, knowledge_points:[string]}。"
                + "题干与解析可使用普通文本，必要时可使用 LaTeX。"
                + "选择题（单选）必须给4个选项(仅 A/B/C/D)，并在answer中返回正确选项字母(A/B/C/D)。"
                + "多选题必须给4个选项(仅 A/B/C/D)，并在answer中返回正确选项字母集合(如AC或BD，不加逗号/空格)。"
                + "判断题必须给2个选项：A.对 与 B.错，并在answer中返回正确选项字母(A/B)。"
                + "填空题answer给唯一标准答案（允许是数字/表达式/短句）。"
                + "解答题answer给关键步骤与结论。"
                + "不要限定为数学学科，按主题生成通用学科题目。";
            String userPrompt = "主题: " + resolvedTopic + "\n难度: " + resolvedDifficulty + "\n题型: " + resolvedQuestionType
                + "\n按主题生成，不要默认数学场景";
            try {
                Map<String, Object> aiResult = chatJson(systemPrompt, userPrompt);
                aiResult.put("question_type", normalizeQuestionType((String) aiResult.get("question_type")));
                aiResult.put("used_fallback", false);
                // 保留需要选项的题型；填空题/解答题不需要 options
                if (!java.util.List.of("选择题", "多选题", "判断题").contains(aiResult.get("question_type"))) {
                    aiResult.put("options", List.of());
                }
                return aiResult;
            } catch (Exception exception) {
                log.warn("AI call failed in generateQuestion, fallback to local template: {}", exception.getMessage());
                usedFallback = true;
                fallbackReason = "ai_failed";
            }
        } else {
            usedFallback = true;
            fallbackReason = "ai_disabled";
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("question_type", resolvedQuestionType);
        response.put("used_fallback", usedFallback);
        response.put("fallback_reason", fallbackReason);
        response.put("resolved_topic", resolvedTopic);
        // 即便兜底模板，也把知识点（topic）写进题干，便于前端识别“是否用到了正确的知识点”
        String topicHint = "【知识点：" + resolvedTopic + "】";
        if ("选择题".equals(resolvedQuestionType)) {
            response.put("question", topicHint + "下列关于该知识点的说法，最符合课程内容的是（ ）。");
            response.put("options", List.of(
                "A. 仅需记忆定义，无需理解应用场景",
                "B. 可结合真实任务场景进行分析与应用",
                "C. 与课程目标完全无关，可忽略",
                "D. 只适用于考试，不适用于实践"
            ));
            response.put("answer", "B");
            response.put("explanation", "该知识点通常需要“概念理解 + 场景应用”结合，B 最符合教学目标。");
        } else if ("填空题".equals(resolvedQuestionType)) {
            response.put("question", topicHint + "请填写该知识点在本课程中的一个核心关键词：____。");
            response.put("options", List.of());
            response.put("answer", "关键概念");
            response.put("explanation", "填空题应围绕该知识点的核心术语或关键步骤作答。");
        } else if ("多选题".equals(resolvedQuestionType)) {
            response.put("question", topicHint + "围绕该知识点开展学习时，以下哪些做法是合理的（ ）。");
            response.put("options", List.of(
                "A. 梳理概念与术语，形成知识框架",
                "B. 结合课程案例进行迁移应用",
                "C. 仅背诵结论，不做任何练习",
                "D. 对错因进行复盘并修正理解"
            ));
            response.put("answer", "ABD");
            response.put("explanation", "A/B/D 都是有效学习策略；C 仅记忆结论、缺少练习与反馈，效果较差。");
        } else if ("判断题".equals(resolvedQuestionType)) {
            response.put("question", topicHint + "判断对错：该知识点只需要死记硬背，不需要理解其应用场景。");
            response.put("options", List.of("A. 对", "B. 错"));
            response.put("answer", "B");
            response.put("explanation", "课程学习通常要求“理解 + 应用 + 反馈”，仅靠记忆不足以完成迁移与实操。");
        } else {
            response.put("question", topicHint + "请结合课程内容，说明该知识点在实际任务中的应用思路与关键步骤。");
            response.put("options", List.of());
            response.put("answer", "可从“概念定义 -> 条件识别 -> 方法选择 -> 执行与验证 -> 结果复盘”五步展开。");
            response.put("explanation", "解答题建议覆盖应用背景、关键步骤、结果说明与可改进点。");
        }
        response.put("knowledge_points", List.of(resolvedTopic + "定义", resolvedTopic + "应用", "结果验证"));
        return response;
    }

    public Map<String, Object> agentChat(String question, String role, String username) {
        String asked = safe(question);
        String roleName = safe(role);
        String name = safe(username);
        Map<String, Object> response = new LinkedHashMap<>();

        if (!StringUtils.hasText(asked)) {
            response.put("answer", "请输入你想咨询的问题。");
            return response;
        }

        String roleText = switch (roleName) {
            case "student" -> "学生";
            case "teacher" -> "教师";
            case "admin" -> "管理员";
            default -> "用户";
        };

        if (!aiEnabled()) {
            response.put("answer", "AI 服务暂未启用，请联系管理员配置 OPENAI_API_KEY 后重试。");
            return response;
        }

        String systemPrompt = """
            你是「AI 学生自学平台」内置助手，只用简体中文回答。
            平台定位：面向学生自学与教师教学管理，含学生端、教师端、管理员端；技术栈对用户一般不重要，除非对方明确问部署/配置。

            你必须只基于以下已存在能力作答，不得编造菜单名、路由或接口：
            - 学生端：课程广场（浏览/加入退出课程、查看任课教师）；知识图谱（交互图谱、学习建议、专业关联、按知识点资料）；知识点交流区（发帖回复点赞；根帖类型含正常帖、答疑帖——答疑帖会通知有该课权限的教师）；顶栏消息通知（答疑/讨论/回复/点赞等，可深链到图谱与交流帖）；AI 练习（单题/套题、测试与考试模式、客观题批改）；错题与学习记录、已保存试卷、公告；个人中心（资料、密码、学习统计）。
            - 教师端：课程广场与市场；课程权限申请（管理员审批后才有课权限）；知识点管理（增删改、层级、Markdown 导入）；教学资料上传与按知识点管理；知识点交流区弹窗（根帖含正常帖、讨论帖——讨论帖通知已加入该课的学生）；顶栏通知并可跳转到课程管理与交流区；公告只读；个人中心。
            - 管理员端：用户与批量导入、公告、课程权重与学分配置、教师课程权限申请审批（顶栏铃铛；同意「新增课程」会写入目录并授权）。
            - 账号：统一登录；注册接口通常关闭（预置或导入账号）；密码与资料修改在平台内提供。
            - AI 能力：出题、批改、学习建议、知识图谱生成等依赖后端配置的 OpenAI 兼容接口；未配置时管理员需设置 OPENAI 相关环境变量。

            回答规范：
            1) 按当前用户角色给出可执行步骤，尽量具体到「在哪个端、大致在什么模块做什么」；
            2) 不确定或仓库未列出的功能，明确说「本平台未提供或不确定」，不要猜测第三方产品名或通用 LMS 功能来冒充本平台；
            3) 简洁分点；隐私/账号安全提醒谨慎操作；
            4) 深链跳转：学生端常用查询参数 dc=课程名、dp=知识点名、dpost=帖子 id（可选）；教师端管理页也可用 dc、dp、dpost 打开对应交流上下文——仅在被问及时提及，勿虚构其它参数。
            """;

        String userPrompt = "当前用户角色: " + roleText +
            (StringUtils.hasText(name) ? ("，用户名: " + name) : "") +
            "\n请严格以上述平台能力为边界回答，不要扩展到无关产品。\n用户问题: " + asked;

        try {
            String answer = chatText(systemPrompt, userPrompt);
            response.put("answer", StringUtils.hasText(answer) ? answer.trim() : "暂时没有可用回答，请稍后重试。");
        } catch (Exception exception) {
            log.warn("agentChat failed: {}", exception.getMessage());
            response.put("answer", "AI 暂时繁忙，请稍后重试。");
        }
        return response;
    }

    /**
     * Batch generate 1-2 questions per request.
     * Each item is the same payload as /generate-question.
     */
    public List<Map<String, Object>> generateQuestions(List<GenerateQuestionRequest> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        int n = Math.min(2, Math.max(0, items.size()));
        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final GenerateQuestionRequest it = items.get(i);
            futures.add(CompletableFuture.supplyAsync(() -> {
                Map<String, Object> q = generateQuestion(
                    it == null ? null : it.topic(),
                    it == null ? null : it.difficulty(),
                    it == null ? null : it.questionType()
                );
                return q == null ? new LinkedHashMap<>() : q;
            }, questionExecutor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        List<Map<String, Object>> out = new ArrayList<>(n);
        for (CompletableFuture<Map<String, Object>> f : futures) {
            out.add(f.join());
        }
        return out;
    }

    public Map<String, Object> gradeAnswer(
        String question,
        String referenceAnswer,
        String studentAnswer,
        String questionType,
        String studentAnswerImageBase64,
        String studentAnswerImageName,
        int fullScore
    ) {
        int resolvedFullScore = Math.max(1, fullScore);
        String resolvedQuestionType = normalizeQuestionType(questionType);
        String mergedStudentAnswer = mergeAnswerWithImageText(studentAnswer, studentAnswerImageBase64, studentAnswerImageName);

        if (java.util.List.of("选择题", "多选题", "判断题", "填空题").contains(resolvedQuestionType)) {
            return gradeObjectiveAnswer(referenceAnswer, mergedStudentAnswer, resolvedFullScore, resolvedQuestionType);
        }

        if (aiEnabled()) {
            String systemPrompt = "你是教学批改助手。输出 JSON:{score:number, strengths:[string], weaknesses:[string], revised_answer:string, summary:string}。评分范围 0 到 full_score。";
            String userPrompt = "题目: " + safe(question) + "\n题型: " + resolvedQuestionType + "\n参考答案: " + safe(referenceAnswer)
                + "\n学生答案: " + safe(mergedStudentAnswer) + "\n满分: " + resolvedFullScore;
            try {
                Map<String, Object> result = chatJson(systemPrompt, userPrompt);
                int score = clampScore(result.get("score"), resolvedFullScore);
                result.put("score", score);
                result.put("full_score", resolvedFullScore);
                return result;
            } catch (Exception exception) {
                log.warn("AI call failed in gradeAnswer, fallback to local template: {}", exception.getMessage());
            }
        }

        List<String> keywords = extractKeywords(referenceAnswer);
        long hitCount = keywords.stream().filter(keyword -> safe(mergedStudentAnswer).contains(keyword)).count();
        double ratio = keywords.isEmpty() ? 0.0 : (double) hitCount / keywords.size();
        int score = safe(mergedStudentAnswer).isBlank() ? 0 : (int) Math.round(resolvedFullScore * Math.min(1.0, 0.45 + ratio * 0.55));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("score", score);
        response.put("full_score", resolvedFullScore);
        response.put("strengths", score >= resolvedFullScore * 0.6 ? List.of("回答结构较完整") : List.of("有一定作答尝试"));
        response.put("weaknesses", List.of("关键术语覆盖不足", "例证不够具体"));
        response.put("revised_answer", "建议围绕题目'" + safe(question) + "'补充定义、步骤和实例，结尾加入结果反思。");
        response.put("summary", "已根据关键词匹配与答案完整度给出启发式评分。");
        return response;
    }

    private Map<String, Object> gradeObjectiveAnswer(String referenceAnswer, String studentAnswer, int fullScore, String questionType) {
        String expected = normalizeObjectiveAnswer(referenceAnswer, questionType);
        String actual = normalizeObjectiveAnswer(studentAnswer, questionType);
        boolean correct = StringUtils.hasText(actual) && actual.equals(expected);
        int score = correct ? fullScore : 0;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("score", score);
        response.put("full_score", fullScore);
        response.put("strengths", correct ? List.of("答案正确") : List.of("有作答尝试"));
        response.put("weaknesses", correct ? List.of() : List.of("最终答案与标准答案不一致"));
        response.put("revised_answer", "标准答案：" + safe(referenceAnswer));
        response.put("summary", correct ? questionType + "自动判分：满分。" : questionType + "自动判分：0分。");
        return response;
    }

    private Map<String, Object> chatJson(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.3);
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        ));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            // 测试/批量生成会多次请求 AI；避免 40s 内未返回导致直接失败
            .timeout(Duration.ofSeconds(90))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("AI request failed with status " + response.statusCode());
        }

        JsonNode payload = objectMapper.readTree(response.body());
        String content = payload.path("choices").path(0).path("message").path("content").asText("");
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("AI response content is empty");
        }
        Map<String, Object> parsed = null;
        try {
            parsed = extractJson(content);
        } catch (Exception e) {
            log.warn("Failed to parse AI JSON response, will attempt heuristics. Error: {}", e.getMessage());
            // fall through to heuristics below
        }

        if (parsed == null) {
            parsed = new LinkedHashMap<>();
        }

        // If parsed result lacks a clear question/stem, try heuristics to extract from raw content
        boolean hasStem = StringUtils.hasText(String.valueOf(parsed.getOrDefault("question", parsed.get("stem"))));
        if (!hasStem) {
            log.warn("AI response missing 'question'/'stem'. Raw content: {}", content.length() > 1000 ? content.substring(0, 1000) + "..." : content);
            String heuristic = extractQuestionFromText(content);
            if (StringUtils.hasText(heuristic)) {
                parsed.put("question", heuristic);
                parsed.put("stem", heuristic);
                log.info("Heuristically extracted question/stem from AI response.");
            }
        }

        return parsed;
    }

    private String chatText(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.5);
        requestBody.put("messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        ));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .timeout(Duration.ofSeconds(90))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("AI request failed with status " + response.statusCode());
        }

        JsonNode payload = objectMapper.readTree(response.body());
        String content = payload.path("choices").path(0).path("message").path("content").asText("");
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("AI response content is empty");
        }
        return content;
    }

    private String extractQuestionFromText(String text) {
        if (text == null) return null;
        // 尝试常见 JSON 字段匹配："question"\s*:\s*"..."
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\\"question\\\"\\s*:\\s*\\\"([\\s\\S]*?)\\\"");
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        // 尝试中文提示样式：题目：... 或 题干：...
        p = java.util.regex.Pattern.compile("(?:题目|题干)[:：\\s]+([\\s\\S]{6,200}?)\\n");
        m = p.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        // 如果未匹配到，返回 null
        return null;
    }

    private Map<String, Object> extractJson(String content) throws IOException {
        String cleaned = content.trim()
            .replaceFirst("^```(?:json)?\\s*", "")
            .replaceFirst("\\s*```$", "");

        try {
            return objectMapper.readValue(cleaned, new TypeReference<>() {
            });
        } catch (IOException exception) {
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return objectMapper.readValue(cleaned.substring(start, end + 1), new TypeReference<>() {
                });
            }
            throw exception;
        }
    }

    private boolean aiEnabled() {
        return StringUtils.hasText(apiKey);
    }

    private String normalizeQuestionType(String questionType) {
        String value = safe(questionType);
        return switch (value) {
            case "选择题", "单选题" -> "选择题";
            case "多选题" -> "多选题";
            case "判断题" -> "判断题";
            case "填空题" -> "填空题";
            case "解答题" -> "解答题";
            case "简答题", "应用题" -> "解答题";
            default -> "解答题";
        };
    }

    /**
     * 将学生单选题作答归一为 A–D 或空串，供测验学情统计（高频错选等）复用判分口径。
     */
    public String normalizeSingleChoiceLetter(String answer) {
        return normalizeObjectiveAnswer(answer, "选择题");
    }

    /**
     * 基于一次知识点测验的聚合数据，生成面向教师的教学建议（简体中文）。
     */
    public String publishedTestTeachingSuggestions(String aggregatedContext, String teacherUsername) {
        String ctx = safe(aggregatedContext);
        if (!StringUtils.hasText(ctx)) {
            return "暂无可用统计数据，无法生成建议。";
        }
        if (!aiEnabled()) {
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
            String out = chatText(systemPrompt, userPrompt);
            return StringUtils.hasText(out) ? out.trim() : "暂时没有可用建议，请稍后重试。";
        } catch (Exception exception) {
            log.warn("publishedTestTeachingSuggestions failed: {}", exception.getMessage());
            return "生成教学建议时 AI 服务异常，请稍后重试。";
        }
    }

    private String normalizeObjectiveAnswer(String answer, String questionType) {
        String value = safe(answer);
        if ("选择题".equals(questionType)) {
            if (value.isBlank()) {
                return "";
            }
            // 兼容 "A" / "A." / "答案：A" / "选A" 等输入（取首个 A-D）
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[A-D]", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value);
            if (m.find()) {
                return m.group().toUpperCase();
            }
            String first = value.substring(0, 1).toUpperCase();
            return List.of("A", "B", "C", "D").contains(first) ? first : value.trim().toUpperCase();
        }
        if ("多选题".equals(questionType)) {
            if (value.isBlank()) {
                return "";
            }
            // 允许输入类似 "AC" / "A,C" / "A C"；归一化为排序后的去重集合，如"AC"
            String upper = value.toUpperCase();
            java.util.Set<String> set = new java.util.HashSet<>();
            for (char ch : upper.toCharArray()) {
                String s = String.valueOf(ch);
                if (List.of("A", "B", "C", "D").contains(s)) {
                    set.add(s);
                }
            }
            // 固定排序，保证 "CA" 归一化为 "AC"
            StringBuilder sb = new StringBuilder();
            for (String letter : List.of("A", "B", "C", "D")) {
                if (set.contains(letter)) sb.append(letter);
            }
            return sb.toString();
        }
        if ("判断题".equals(questionType)) {
            if (value.isBlank()) {
                return "";
            }
            String upper = value.trim().toUpperCase();
            // 兼容 "A/B"、"对/错"、"√/×"、"T/F"
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[AB]", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(upper);
            if (m.find()) {
                return m.group().toUpperCase();
            }
            // 兼容中文/英文真值
            if (upper.contains("对") || upper.contains("TRUE") || upper.contains("T") || upper.contains("√")) return "A";
            if (upper.contains("错") || upper.contains("FALSE") || upper.contains("F") || upper.contains("×") || upper.contains("✗")) return "B";
            return upper.length() >= 1 ? upper.substring(0, 1) : "";
        }
        return value.replaceAll("\\s+", "").toLowerCase();
    }

    private String mergeAnswerWithImageText(String studentAnswer, String imageBase64, String imageName) {
        String typed = safe(studentAnswer);
        String ocr = extractTextFromImage(imageBase64, imageName);
        if (!StringUtils.hasText(ocr)) {
            return typed;
        }
        if (!StringUtils.hasText(typed)) {
            return ocr;
        }
        return typed + "\n" + ocr;
    }

    private String extractTextFromImage(String imageBase64, String imageName) {
        if (!aiEnabled() || !StringUtils.hasText(imageBase64)) {
            return "";
        }

        try {
            String dataUri = imageBase64.trim();
            if (!dataUri.startsWith("data:")) {
                dataUri = "data:image/png;base64," + dataUri;
            }

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.0);
            requestBody.put("response_format", Map.of("type", "json_object"));
            requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "你是OCR助手。请从图片中提取学生作答文本，输出JSON: {text: string}。"),
                Map.of(
                    "role", "user",
                    "content", List.of(
                        Map.of("type", "text", "text", "请识别这张学生作答图片中的文字内容。文件名: " + safe(imageName)),
                        Map.of("type", "image_url", "image_url", Map.of("url", dataUri))
                    )
                )
            ));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(40))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "";
            }

            JsonNode payload = objectMapper.readTree(response.body());
            String content = payload.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                return "";
            }
            return String.valueOf(extractJson(content).getOrDefault("text", "")).trim();
        } catch (Exception exception) {
            log.warn("OCR extraction failed: {}", exception.getMessage());
            return "";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private int clampScore(Object scoreValue, int fullScore) {
        if (scoreValue instanceof Number number) {
            return Math.max(0, Math.min(fullScore, (int) Math.round(number.doubleValue())));
        }
        try {
            return Math.max(0, Math.min(fullScore, (int) Math.round(Double.parseDouble(String.valueOf(scoreValue)))));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private List<String> extractKeywords(String referenceAnswer) {
        String normalized = safe(referenceAnswer).replace('，', ' ').replace('。', ' ').replace(',', ' ').replace('.', ' ');
        String[] parts = normalized.split("\\s+");
        List<String> keywords = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                keywords.add(part);
            }
        }
        return keywords;
    }

    private String maskKey(String key) {
        if (!StringUtils.hasText(key)) {
            return "(empty)";
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "..." + key.substring(key.length() - 4);
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
}
