package com.teacher.backend.controller;
import java.util.Map;
import java.util.List;
import com.teacher.backend.dto.GenerateQuestionRequest;
import com.teacher.backend.dto.GenerateQuestionsRequest;
import com.teacher.backend.dto.GenerateTestRequest;
import com.teacher.backend.dto.GenerateExamRequest;
import com.teacher.backend.dto.GradeAnswerRequest;
import com.teacher.backend.dto.KnowledgeGraphRequest;
import com.teacher.backend.dto.LearningSuggestionsRequest;
import com.teacher.backend.dto.MajorRelevanceRequest;
import com.teacher.backend.dto.SaveExamRequest;
import com.teacher.backend.service.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.teacher.backend.repository.GeneratedExamRepository generatedExamRepository;

    @PostMapping("/knowledge-graph")
    public Map<String, Object> knowledgeGraph(@RequestBody(required = false) KnowledgeGraphRequest request) {
        log.info("Hit /api/knowledge-graph, topic={}", request == null ? null : request.topic());
        return aiService.generateKnowledgeGraph(request == null ? null : request.topic());
    }

    @PostMapping("/learning-suggestions")
    public Map<String, Object> learningSuggestions(@RequestBody(required = false) LearningSuggestionsRequest request) {
        String topic = request == null ? null : request.topic();
        String knowledgePoint = request == null ? null : request.knowledgePoint();
        log.info("Hit /api/learning-suggestions, topic={}, knowledgePoint={}", topic, knowledgePoint);
        return aiService.generateLearningSuggestions(topic, knowledgePoint);
    }

    @PostMapping("/major-relevance")
    public Map<String, Object> majorRelevance(@RequestBody(required = false) MajorRelevanceRequest request) {
        String topic = request == null ? null : request.topic();
        String knowledgePoint = request == null ? null : request.knowledgePoint();
        String major = request == null ? null : request.major();
        log.info("Hit /api/major-relevance, topic={}, knowledgePoint={}, major={}", topic, knowledgePoint, major);
        return aiService.generateMajorRelevance(topic, knowledgePoint, major);
    }

    @PostMapping("/generate-question")
    public Map<String, Object> generateQuestion(@RequestBody(required = false) GenerateQuestionRequest request) {
        log.info("Hit /api/generate-question, topic={}, difficulty={}, type={}, major={}",
            request == null ? null : request.topic(),
            request == null ? null : request.difficulty(),
            request == null ? null : request.questionType(),
            request == null ? null : request.major());
        return aiService.generateQuestion(
            request == null ? null : request.topic(),
            request == null ? null : request.difficulty(),
            request == null ? null : request.questionType(),
            request == null ? null : request.major()
        );
    }

    /**
     * Batch generate 1-2 questions per request.
     * This is used by student-side "测试模式" to cap API requests (<=5) while still generating up to 10 questions.
     */
    @PostMapping("/generate-questions")
    public Map<String, Object> generateQuestions(@RequestBody(required = false) GenerateQuestionsRequest request) {
        int size = (request == null || request.items() == null) ? 0 : request.items().size();
        log.info("Hit /api/generate-questions, items={}", size);
        List<Map<String, Object>> questions = aiService.generateQuestions(request == null ? null : request.items());
        return Map.of("questions", questions);
    }

    @PostMapping("/grade-answer")
    public Map<String, Object> gradeAnswer(@RequestBody(required = false) GradeAnswerRequest request) {
        log.info("Hit /api/grade-answer, questionLength={}, answerLength={}, type={}",
            request == null || request.question() == null ? 0 : request.question().length(),
            request == null || request.studentAnswer() == null ? 0 : request.studentAnswer().length(),
            request == null ? null : request.questionType());
        return aiService.gradeAnswer(
            request == null ? null : request.question(),
            request == null ? null : request.referenceAnswer(),
            request == null ? null : request.studentAnswer(),
            request == null ? null : request.questionType(),
            request == null ? null : request.studentAnswerImageBase64(),
            request == null ? null : request.studentAnswerImageName(),
            request == null || request.fullScore() == null ? 10 : request.fullScore()
        );
    }

    @PostMapping("/generate-test")
    public Map<String, Object> generateTest(@RequestBody(required = false) GenerateTestRequest request) {
        log.info("Hit /api/generate-test, topic={}, gradeLevel={}, count={}",
            request == null ? null : request.topic(),
            request == null ? null : request.gradeLevel(),
            request == null ? null : request.count());
        return aiService.generateTest(
            request == null ? null : request.topic(),
            request == null ? null : request.gradeLevel(),
            request == null || request.count() == null ? 8 : request.count()
        );
    }

    @PostMapping("/generate-exam")
    public Map<String, Object> generateExam(@RequestBody(required = false) GenerateExamRequest request) {
        log.info("Hit /api/generate-exam, points={}, choice={}, fill={}, essay={}",
            request == null ? null : request.knowledgePoints(),
            request == null ? null : request.choiceCount(),
            request == null ? null : request.fillCount(),
            request == null ? null : request.essayCount());
        Map<String, Object> result = aiService.generateExam(
            request == null ? List.of() : request.knowledgePoints(),
            request == null || request.choiceCount() == null ? 0 : request.choiceCount(),
            request == null || request.fillCount() == null ? 0 : request.fillCount(),
            request == null || request.essayCount() == null ? 0 : request.essayCount(),
            request == null ? "试卷" : (request.title() == null ? "试卷" : request.title()),
            request == null || request.durationMinutes() == null ? 60 : request.durationMinutes()
        );
        return result;
    }

    @PostMapping("/exams/save")
    public Map<String, Object> saveExam(@RequestBody(required = false) SaveExamRequest request) {
        int size = (request == null || request.questions() == null) ? 0 : request.questions().size();
        log.info("Hit /api/exams/save, title={}, durationMinutes={}, questions={}",
            request == null ? null : request.title(),
            request == null ? null : request.durationMinutes(),
            size);
        return aiService.saveExamFromQuestions(
            request == null ? null : request.title(),
            request == null ? null : request.durationMinutes(),
            request == null ? List.of() : request.questions()
        );
    }

    @GetMapping("/exams")
    public java.util.List<Map<String, Object>> listExams() {
        try {
            java.util.List<com.teacher.backend.entity.GeneratedExam> list = generatedExamRepository.findAll();
            java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
            for (var e : list) {
                var m = new java.util.LinkedHashMap<String, Object>();
                // allow null id to be represented as null instead of throwing
                m.put("id", e.getId());
                m.put("title", e.getTitle());
                m.put("durationMinutes", e.getDurationMinutes());
                m.put("createdAt", e.getCreatedAt());
                        m.put("hasPaperPdf", e.getPdfOriginal() != null);
                        m.put("hasAnswerPdf", e.getPdfAnswer() != null);
                        m.put("mdPaper", e.getMdOriginal() != null);
                        m.put("mdAnswer", e.getMdAnswer() != null);
                out.add(m);
            }
            return out;
        } catch (Exception ex) {
            return java.util.Collections.emptyList();
        }
    }

    @GetMapping("/exams/{id}/download")
    public org.springframework.http.ResponseEntity<byte[]> downloadExamPdf(@org.springframework.web.bind.annotation.PathVariable Long id,
        @org.springframework.web.bind.annotation.RequestParam(defaultValue = "paper") String type) {
        if (id == null) return org.springframework.http.ResponseEntity.badRequest().build();
        var maybe = generatedExamRepository.findById(id);
        if (maybe.isEmpty()) return org.springframework.http.ResponseEntity.notFound().build();
        var exam = maybe.get();
        byte[] data = null;
        String filename = null;
        org.springframework.http.MediaType mediaType = null;
        // Normalize 'type' values: treat "md" as md_paper
        String t = type == null ? "paper" : type;
        if ("md".equals(t) || "md_paper".equals(t)) {
            String md = exam.getMdOriginal();
            data = md == null ? null : md.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            filename = (exam.getTitle() == null ? "exam" : exam.getTitle()) + ".md";
            mediaType = org.springframework.http.MediaType.parseMediaType("text/markdown; charset=UTF-8");
        } else if ("md_answer".equals(t)) {
            String md = exam.getMdAnswer();
            data = md == null ? null : md.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            filename = (exam.getTitle() == null ? "exam" : exam.getTitle()) + "_answer.md";
            mediaType = org.springframework.http.MediaType.parseMediaType("text/markdown; charset=UTF-8");
        } else if ("answer".equals(t)) {
            data = exam.getPdfAnswer();
            filename = (exam.getTitle() == null ? "exam" : exam.getTitle()) + "_answer.pdf";
            mediaType = org.springframework.http.MediaType.APPLICATION_PDF;
        } else {
            // default to paper pdf
            data = exam.getPdfOriginal();
            filename = (exam.getTitle() == null ? "exam" : exam.getTitle()) + ".pdf";
            mediaType = org.springframework.http.MediaType.APPLICATION_PDF;
        }
    
        if (data == null) return org.springframework.http.ResponseEntity.notFound().build();
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(mediaType);
        try {
            org.springframework.http.ContentDisposition cd = org.springframework.http.ContentDisposition
                .attachment()
                .filename(filename, java.nio.charset.StandardCharsets.UTF_8)
                .build();
            headers.setContentDisposition(cd);
        } catch (Exception ex) {
            // fallback to older header format
            headers.add("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        }
        return new org.springframework.http.ResponseEntity<>(data, headers, org.springframework.http.HttpStatus.OK);
    }

    @PostMapping("/exams/{id}/render")
    public Map<String, Object> renderExam(@org.springframework.web.bind.annotation.PathVariable Long id) {
        try {
            if (id == null) {
                var bad = new java.util.LinkedHashMap<String, Object>();
                    bad.put("mdPaper", false);
                    bad.put("mdAnswer", false);
                bad.put("error", "missing-id");
                return bad;
            }
            // Only generate Markdown here; PDF generation was removed
                return aiService.renderSavedExamDocs(id);
        } catch (Exception ex) {
            var m = new java.util.LinkedHashMap<String, Object>();
                m.put("mdPaper", false);
                m.put("mdAnswer", false);
            m.put("error", ex.getMessage());
            return m;
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/exams/{id}")
    public org.springframework.http.ResponseEntity<?> deleteExam(@org.springframework.web.bind.annotation.PathVariable Long id) {
        try {
            if (id == null) return org.springframework.http.ResponseEntity.badRequest().build();
            var maybe = generatedExamRepository.findById(id);
            if (maybe.isEmpty()) return org.springframework.http.ResponseEntity.notFound().build();
            generatedExamRepository.deleteById(id);
            return org.springframework.http.ResponseEntity.noContent().build();
        } catch (Exception ex) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of("error", ex.getMessage()));
        }
    }
}
