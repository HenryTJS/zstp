package com.teacher.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.teacher.backend.dto.GenerateQuestionRequest;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Service for AI-powered question generation.
 */
@Service
public class AiQuestionService {

    private static final Logger log = LoggerFactory.getLogger(AiQuestionService.class);

    private final AiClient aiClient;
    private final ExecutorService questionExecutor = Executors.newFixedThreadPool(4);

    public AiQuestionService(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    @PreDestroy
    public void shutdown() {
        try {
            questionExecutor.shutdown();
        } catch (Exception ignored) {
        }
    }

    /**
     * Generate a single question using AI or fallback template.
     */
    public Map<String, Object> generateQuestion(String topic, String difficulty, String questionType, String knowledgePointDescription) {
        String resolvedTopic = StringUtils.hasText(topic) ? topic.trim() : "通用知识点";
        String resolvedDifficulty = StringUtils.hasText(difficulty) ? difficulty.trim() : "中等";
        String resolvedQuestionType = normalizeQuestionType(questionType);
        boolean usedFallback = false;
        String fallbackReason = "";

        if (aiClient.aiEnabled()) {
            String systemPrompt = "你是教学命题助手。严格遵循以下原则：\n"
                + "1. 题目必须紧密围绕用户指定的「知识点」本身的核心内容，直接考查该知识点的概念、原理或计算\n"
                + "2. 如果知识点是纯数学/纯理论内容（如不定积分、矩阵运算、微积分等），直接出纯题目，不得强行套用物理/工程/经济等应用场景\n"
                + "3. 除非用户明确要求「应用题」或「结合实际场景」，否则默认出直接考查该知识点的标准题目\n"
                + "4. 题目难度需符合用户指定的难度等级（基础/中等/拔高）\n"
                + "仅支持题型: 选择题/多选题/判断题/填空题/解答题（其中选择题=单选题）。"
                + "输出 JSON:{question:string, question_type:string, options:[string], answer:string, explanation:string, knowledge_points:[string]}。\n"
                + "题干与解析可使用普通文本，必要时可使用 LaTeX。\n"
                + "选择题（单选）必须给4个选项(仅 A/B/C/D)，并在answer中返回正确选项字母(A/B/C/D)。\n"
                + "多选题必须给4个选项(仅 A/B/C/D)，并在answer中返回正确选项字母集合(如AC或BD，不加逗号/空格)。\n"
                + "判断题必须给2个选项：A.对 与 B.错，并在answer中返回正确选项字母(A/B)。\n"
                + "填空题answer给唯一标准答案（允许是数字/表达式/短句）。\n"
                + "解答题answer给关键步骤与结论。";
            String userPrompt = "课程: " + resolvedTopic
                + "\n难度: " + resolvedDifficulty
                + "\n题型: " + resolvedQuestionType;
            if (StringUtils.hasText(knowledgePointDescription)) {
                userPrompt += "\n知识点描述: " + knowledgePointDescription.trim();
            }
            userPrompt += "\n要求：直接考查该知识点本身的核心内容，不要引入其他学科背景或应用场景";
            try {
                Map<String, Object> aiResult = aiClient.chatJson(systemPrompt, userPrompt);
                aiResult.put("question_type", normalizeQuestionType((String) aiResult.get("question_type")));
                aiResult.put("used_fallback", false);
                if (!List.of("选择题", "多选题", "判断题").contains(aiResult.get("question_type"))) {
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
            response.put("explanation", "该知识点通常需要「概念理解 + 场景应用」结合，B 最符合教学目标。");
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
            response.put("explanation", "课程学习通常要求「理解 + 应用 + 反馈」，仅靠记忆不足以完成迁移与实操。");
        } else {
            response.put("question", topicHint + "请结合课程内容，说明该知识点在实际任务中的应用思路与关键步骤。");
            response.put("options", List.of());
            response.put("answer", "可从「概念定义 -> 条件识别 -> 方法选择 -> 执行与验证 -> 结果复盘」五步展开。");
            response.put("explanation", "解答题建议覆盖应用背景、关键步骤、结果说明与可改进点。");
        }
        response.put("knowledge_points", List.of(resolvedTopic + "定义", resolvedTopic + "应用", "结果验证"));
        return response;
    }

    /**
     * Batch generate 1-2 questions per request.
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
                    it == null ? null : it.questionType(),
                    it == null ? null : it.knowledgePointDescription()
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

    public String normalizeQuestionType(String questionType) {
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
     * Normalize single-choice answer to A-D letter.
     */
    public String normalizeSingleChoiceLetter(String answer) {
        return normalizeObjectiveAnswer(answer, "选择题");
    }

    public String normalizeObjectiveAnswer(String answer, String questionType) {
        String value = safe(answer);
        if ("选择题".equals(questionType)) {
            if (value.isBlank()) return "";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[A-D]", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value);
            if (m.find()) return m.group().toUpperCase();
            String first = value.substring(0, 1).toUpperCase();
            return List.of("A", "B", "C", "D").contains(first) ? first : value.trim().toUpperCase();
        }
        if ("多选题".equals(questionType)) {
            if (value.isBlank()) return "";
            String upper = value.toUpperCase();
            java.util.Set<String> set = new java.util.HashSet<>();
            for (char ch : upper.toCharArray()) {
                String s = String.valueOf(ch);
                if (List.of("A", "B", "C", "D").contains(s)) set.add(s);
            }
            StringBuilder sb = new StringBuilder();
            for (String letter : List.of("A", "B", "C", "D")) {
                if (set.contains(letter)) sb.append(letter);
            }
            return sb.toString();
        }
        if ("判断题".equals(questionType)) {
            if (value.isBlank()) return "";
            String upper = value.trim().toUpperCase();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[AB]", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(upper);
            if (m.find()) return m.group().toUpperCase();
            if (upper.contains("对") || upper.contains("TRUE") || upper.contains("T") || upper.contains("√")) return "A";
            if (upper.contains("错") || upper.contains("FALSE") || upper.contains("F") || upper.contains("×") || upper.contains("✗")) return "B";
            return upper.length() >= 1 ? upper.substring(0, 1) : "";
        }
        return value.replaceAll("\\s+", "").toLowerCase();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
