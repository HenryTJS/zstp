package com.teacher.backend.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teacher.backend.entity.GeneratedExam;
import com.teacher.backend.repository.GeneratedExamRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for exam-related operations: saving, rendering, and Markdown generation.
 */
@Service
public class ExamService {

    private static final Logger log = LoggerFactory.getLogger(ExamService.class);

    private final GeneratedExamRepository generatedExamRepository;
    private final ObjectMapper objectMapper;

    public ExamService(GeneratedExamRepository generatedExamRepository, ObjectMapper objectMapper) {
        this.generatedExamRepository = generatedExamRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate and persist Markdown (original and answer) for an existing GeneratedExam.
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
            var maybe = generatedExamRepository.findById(examId);
            if (maybe.isEmpty()) {
                out.put("error", "not-found");
                return out;
            }
            var ge = maybe.get();
            List<Map<String, Object>> questions;
            try {
                if (ge.getQuestionsJson() != null) {
                    questions = objectMapper.readValue(ge.getQuestionsJson(), new TypeReference<List<Map<String, Object>>>() {});
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
                    ge.setMdOriginal(new String(mdOrig, StandardCharsets.UTF_8));
                    out.put("mdPaper", true);
                    updated = true;
                }
                byte[] mdAns = createMarkdownFromQuestions(ge.getTitle(), questions, true);
                if (mdAns != null) {
                    ge.setMdAnswer(new String(mdAns, StandardCharsets.UTF_8));
                    out.put("mdAnswer", true);
                    updated = true;
                }
            } catch (Exception ex) {
                log.debug("Markdown generation failed for exam {}: {}", examId, ex.getMessage());
            }

            if (updated) {
                generatedExamRepository.save(ge);
            }
        } catch (Exception ex) {
            log.warn("renderSavedExamDocs error: {}", ex.getMessage());
            out.put("error", "exception");
        }
        return out;
    }

    /**
     * Persist an exam from frontend-provided questions and generate Markdown docs.
     */
    public Map<String, Object> saveExamFromQuestions(String title, Integer durationMinutes, List<Map<String, Object>> questions) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("examId", null);
        response.put("mdOriginalPresent", false);
        response.put("mdAnswerPresent", false);

        String resolvedTitle = (title == null || title.isBlank()) ? "试卷" : title.trim();
        int resolvedDuration = durationMinutes == null ? 60 : Math.max(10, Math.min(240, durationMinutes));
        List<Map<String, Object>> src = questions == null ? List.of() : questions;

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
            if (q.containsKey("knowledge_points")) {
                m.put("knowledge_points", q.get("knowledge_points"));
            }
            normalized.add(m);
            idx++;
            if (idx > 50) break;
        }

        try {
            GeneratedExam ge = new GeneratedExam();
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
                if (mdOrig != null) ge.setMdOriginal(new String(mdOrig, StandardCharsets.UTF_8));
            } catch (Exception ex) {
                log.debug("saveExamFromQuestions: createMarkdownFromQuestions(original) failed: {}", ex.getMessage());
            }
            try {
                byte[] mdAns = createMarkdownFromQuestions(ge.getTitle(), normalized, true);
                if (mdAns != null) ge.setMdAnswer(new String(mdAns, StandardCharsets.UTF_8));
            } catch (Exception ex) {
                log.debug("saveExamFromQuestions: createMarkdownFromQuestions(answer) failed: {}", ex.getMessage());
            }

            GeneratedExam saved = generatedExamRepository.save(ge);
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

    public byte[] createMarkdownFromQuestions(String title, List<Map<String, Object>> questions, boolean includeAnswers) throws Exception {
        if (questions == null) questions = List.of();
        StringBuilder md = new StringBuilder();
        md.append("# ").append(title == null ? "试卷" : title).append("\n\n");
        md.append("**题目数量:** ").append(questions.size()).append("\n\n");

        int idx = 1;
        for (Map<String, Object> q : questions) {
            md.append("## 题 ").append(idx++).append("（").append(q.getOrDefault("score", "")).append(" 分）\n\n");
            md.append(String.valueOf(q.getOrDefault("stem", ""))).append("\n\n");

            @SuppressWarnings("unchecked")
            List<String> opts = (List<String>) q.getOrDefault("options", List.of());
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

        return md.toString().getBytes(StandardCharsets.UTF_8);
    }

    public int resolvePerQuestionScore(Map<String, Object> q, String questionType) {
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

    public int guessScoreByType(String questionType) {
        String t = safe(questionType);
        return switch (t) {
            case "选择题", "单选题" -> 6;
            case "多选题" -> 6;
            case "判断题" -> 3;
            case "填空题" -> 6;
            case "简答题" -> 8;
            default -> 16;
        };
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
