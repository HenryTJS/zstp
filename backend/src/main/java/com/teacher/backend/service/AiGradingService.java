package com.teacher.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Service for AI-powered answer grading and scoring.
 */
@Service
public class AiGradingService {

    private static final Logger log = LoggerFactory.getLogger(AiGradingService.class);

    private final AiClient aiClient;

    public AiGradingService(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    /**
     * Grade a student's answer using AI or fallback keyword matching.
     */
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

        if (List.of("选择题", "多选题", "判断题", "填空题").contains(resolvedQuestionType)) {
            return gradeObjectiveAnswer(referenceAnswer, mergedStudentAnswer, resolvedFullScore, resolvedQuestionType);
        }

        if (aiClient.aiEnabled()) {
            String systemPrompt = "你是教学批改助手。输出 JSON:{score:number, strengths:[string], weaknesses:[string], revised_answer:string, summary:string}。评分范围 0 到 full_score。";
            String userPrompt = "题目: " + safe(question) + "\n题型: " + resolvedQuestionType + "\n参考答案: " + safe(referenceAnswer)
                + "\n学生答案: " + safe(mergedStudentAnswer) + "\n满分: " + resolvedFullScore;
            try {
                Map<String, Object> result = aiClient.chatJson(systemPrompt, userPrompt);
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

    /**
     * Grade objective-type answers (choice, multi-choice, true/false, fill-in).
     */
    public Map<String, Object> gradeObjectiveAnswer(String referenceAnswer, String studentAnswer, int fullScore, String questionType) {
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

    /**
     * Merge typed student answer with OCR text from image.
     */
    public String mergeAnswerWithImageText(String studentAnswer, String imageBase64, String imageName) {
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

    /**
     * Extract text from student answer image using AI vision.
     */
    public String extractTextFromImage(String imageBase64, String imageName) {
        if (!aiClient.aiEnabled() || !StringUtils.hasText(imageBase64)) {
            return "";
        }
        try {
            String systemPrompt = "你是OCR助手。请从图片中提取学生作答文本，输出JSON: {text: string}。";
            return aiClient.chatWithImage(systemPrompt, imageBase64, imageName);
        } catch (Exception exception) {
            log.warn("OCR extraction failed: {}", exception.getMessage());
            return "";
        }
    }

    /**
     * Extract keywords from reference answer for fallback scoring.
     */
    public List<String> extractKeywords(String referenceAnswer) {
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

    /**
     * Clamp score value between 0 and fullScore.
     */
    public int clampScore(Object scoreValue, int fullScore) {
        if (scoreValue instanceof Number number) {
            return Math.max(0, Math.min(fullScore, (int) Math.round(number.doubleValue())));
        }
        try {
            return Math.max(0, Math.min(fullScore, (int) Math.round(Double.parseDouble(String.valueOf(scoreValue)))));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    /**
     * Normalize objective answer for comparison.
     */
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

    /**
     * Normalize single-choice answer to A-D letter.
     */
    public String normalizeSingleChoiceLetter(String answer) {
        return normalizeObjectiveAnswer(answer, "选择题");
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
