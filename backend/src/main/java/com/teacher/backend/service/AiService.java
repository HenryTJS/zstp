package com.teacher.backend.service;

import java.util.List;
import java.util.Map;

import com.teacher.backend.dto.GenerateQuestionRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Facade service that delegates to specialized sub-services.
 * <p>
 * This class exists for backward compatibility with AiController and any other
 * existing callers. New code should inject the specific service directly.
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final AiQuestionService aiQuestionService;
    private final AiGradingService aiGradingService;
    private final AiSuggestionService aiSuggestionService;
    private final AiChatService aiChatService;
    private final ExamService examService;

    public AiService(
        AiQuestionService aiQuestionService,
        AiGradingService aiGradingService,
        AiSuggestionService aiSuggestionService,
        AiChatService aiChatService,
        ExamService examService
    ) {
        this.aiQuestionService = aiQuestionService;
        this.aiGradingService = aiGradingService;
        this.aiSuggestionService = aiSuggestionService;
        this.aiChatService = aiChatService;
        this.examService = examService;
        log.info("AiService facade initialized, delegating to sub-services.");
    }

    // ==================== Question Generation ====================

    public Map<String, Object> generateQuestion(String topic, String difficulty, String questionType, String knowledgePointDescription) {
        return aiQuestionService.generateQuestion(topic, difficulty, questionType, knowledgePointDescription);
    }

    public List<Map<String, Object>> generateQuestions(List<GenerateQuestionRequest> items) {
        return aiQuestionService.generateQuestions(items);
    }

    // ==================== Grading ====================

    public Map<String, Object> gradeAnswer(
        String question,
        String referenceAnswer,
        String studentAnswer,
        String questionType,
        String studentAnswerImageBase64,
        String studentAnswerImageName,
        int fullScore
    ) {
        return aiGradingService.gradeAnswer(question, referenceAnswer, studentAnswer, questionType,
            studentAnswerImageBase64, studentAnswerImageName, fullScore);
    }

    /**
     * Normalize single-choice answer to A-D letter.
     * Delegated to AiGradingService for backward compatibility.
     */
    public String normalizeSingleChoiceLetter(String answer) {
        return aiGradingService.normalizeSingleChoiceLetter(answer);
    }

    /**
     * Normalize objective answer for comparison.
     * Delegated to AiGradingService for backward compatibility.
     */
    public String normalizeObjectiveAnswer(String answer, String questionType) {
        return aiGradingService.normalizeObjectiveAnswer(answer, questionType);
    }

    // ==================== Suggestions & Knowledge Graph ====================

    public Map<String, Object> generateKnowledgeGraph(String topic) {
        return aiSuggestionService.generateKnowledgeGraph(topic);
    }

    public Map<String, Object> generateLearningSuggestions(String topic, String knowledgePoint) {
        return aiSuggestionService.generateLearningSuggestions(topic, knowledgePoint);
    }

    public Map<String, Object> generateMajorRelevance(String topic, String knowledgePoint, String major) {
        return aiSuggestionService.generateMajorRelevance(topic, knowledgePoint, major);
    }

    public String publishedTestTeachingSuggestions(String aggregatedContext, String teacherUsername) {
        return aiSuggestionService.publishedTestTeachingSuggestions(aggregatedContext, teacherUsername);
    }

    // ==================== Chat ====================

    public Map<String, Object> agentChat(String question, String role, String username) {
        return aiChatService.agentChat(question, role, username);
    }

    // ==================== Exam ====================

    public Map<String, Object> renderSavedExamDocs(Long examId) {
        return examService.renderSavedExamDocs(examId);
    }

    public Map<String, Object> saveExamFromQuestions(String title, Integer durationMinutes, List<Map<String, Object>> questions) {
        return examService.saveExamFromQuestions(title, durationMinutes, questions);
    }
}
