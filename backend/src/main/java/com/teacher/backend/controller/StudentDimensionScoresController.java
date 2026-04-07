package com.teacher.backend.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teacher.backend.dto.CourseConfigDto;
import com.teacher.backend.dto.CourseDimensionWeightsDto;
import com.teacher.backend.dto.StudentDimensionScoresResponse;
import com.teacher.backend.entity.StudentState;
import com.teacher.backend.entity.User;
import com.teacher.backend.repository.StudentStateRepository;
import com.teacher.backend.repository.UserRepository;
import com.teacher.backend.service.CourseCatalogService;
import com.teacher.backend.service.CourseConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student-dimension-scores")
public class StudentDimensionScoresController {

    private final UserRepository userRepository;
    private final StudentStateRepository studentStateRepository;
    private final CourseCatalogService courseCatalogService;
    private final CourseConfigService courseConfigService;
    private final ObjectMapper objectMapper;

    public StudentDimensionScoresController(
        UserRepository userRepository,
        StudentStateRepository studentStateRepository,
        CourseCatalogService courseCatalogService,
        CourseConfigService courseConfigService,
        ObjectMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.studentStateRepository = studentStateRepository;
        this.courseCatalogService = courseCatalogService;
        this.courseConfigService = courseConfigService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<?> getScores(@RequestParam(required = false) Long userId, @RequestParam(required = false) String course) {
        if (userId == null) {
            return error(HttpStatus.BAD_REQUEST, "userId is required");
        }
        User user = userRepository.findByIdAndRole(userId, "student").orElse(null);
        if (user == null) {
            return error(HttpStatus.NOT_FOUND, "student user not found");
        }

        StudentState state = studentStateRepository.findByUserId(userId).orElse(null);
        if (state == null) {
            return ResponseEntity.ok(empty(userId, null, course));
        }

        String majorCode = state.getMajor();
        List<Map<String, Object>> learningRecords = parseJsonArray(state.getLearningRecordsJson());
        if (learningRecords.isEmpty()) {
            return ResponseEntity.ok(empty(userId, majorCode, course));
        }

        String scopeCourse = StringUtils.hasText(course) ? courseCatalogService.normalizeCourseName(course) : null;

        Map<String, double[]> aggByCourse = new LinkedHashMap<>();
        for (Map<String, Object> row : learningRecords) {
            String c = row.get("course") == null ? null : String.valueOf(row.get("course"));
            if (!StringUtils.hasText(c)) {
                continue;
            }
            String normalized = courseCatalogService.normalizeCourseName(c);
            if (scopeCourse != null && !Objects.equals(scopeCourse, normalized)) {
                continue;
            }

            double score = toNumber(row.get("score"));
            double full = toNumber(row.get("fullScore"));
            if (full <= 0) {
                continue;
            }
            double[] pair = aggByCourse.computeIfAbsent(normalized, k -> new double[] {0, 0});
            pair[0] += Math.max(0, score);
            pair[1] += Math.max(0, full);
        }

        if (aggByCourse.isEmpty()) {
            return ResponseEntity.ok(empty(userId, majorCode, scopeCourse));
        }

        // per dimension numerator/denominator
        double lrNum = 0, lrDen = 0;
        double ncNum = 0, ncDen = 0;
        double suNum = 0, suDen = 0;
        double siNum = 0, siDen = 0;
        double mrNum = 0, mrDen = 0;

        List<Map<String, Object>> usedCourses = new ArrayList<>();

        for (Map.Entry<String, double[]> entry : aggByCourse.entrySet()) {
            String c = entry.getKey();
            double scoreSum = entry.getValue()[0];
            double fullSum = entry.getValue()[1];
            if (fullSum <= 0) continue;
            double accuracy = clamp01(scoreSum / fullSum);

            CourseConfigDto config = courseConfigService.getOrDefaultConfig(c);
            CourseDimensionWeightsDto w = config.weights();
            double credit = courseConfigService.resolveCreditForStudentMajor(c, majorCode);

            double lrW = credit * w.logicReasoning();
            double ncW = credit * w.numericCalculation();
            double suW = credit * w.semanticUnderstanding();
            double siW = credit * w.spatialImagination();
            double mrW = credit * w.memoryRetrieval();

            lrNum += accuracy * lrW; lrDen += lrW;
            ncNum += accuracy * ncW; ncDen += ncW;
            suNum += accuracy * suW; suDen += suW;
            siNum += accuracy * siW; siDen += siW;
            mrNum += accuracy * mrW; mrDen += mrW;

            usedCourses.add(Map.of(
                "courseName", c,
                "accuracy", round2(accuracy * 100),
                "score", round2(scoreSum),
                "full", round2(fullSum),
                "credit", credit,
                "weights", Map.of(
                    "logicReasoning", w.logicReasoning(),
                    "numericCalculation", w.numericCalculation(),
                    "semanticUnderstanding", w.semanticUnderstanding(),
                    "spatialImagination", w.spatialImagination(),
                    "memoryRetrieval", w.memoryRetrieval()
                )
            ));
        }

        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("logicReasoning", lrDen <= 0 ? 0 : round2((lrNum / lrDen) * 100));
        scores.put("numericCalculation", ncDen <= 0 ? 0 : round2((ncNum / ncDen) * 100));
        scores.put("semanticUnderstanding", suDen <= 0 ? 0 : round2((suNum / suDen) * 100));
        scores.put("spatialImagination", siDen <= 0 ? 0 : round2((siNum / siDen) * 100));
        scores.put("memoryRetrieval", mrDen <= 0 ? 0 : round2((mrNum / mrDen) * 100));

        Map<String, Double> den = new LinkedHashMap<>();
        den.put("logicReasoning", round2(lrDen));
        den.put("numericCalculation", round2(ncDen));
        den.put("semanticUnderstanding", round2(suDen));
        den.put("spatialImagination", round2(siDen));
        den.put("memoryRetrieval", round2(mrDen));

        return ResponseEntity.ok(new StudentDimensionScoresResponse(
            userId,
            majorCode,
            scopeCourse,
            scores,
            den,
            usedCourses
        ));
    }

    private StudentDimensionScoresResponse empty(Long userId, String majorCode, String scopeCourse) {
        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("logicReasoning", 0d);
        scores.put("numericCalculation", 0d);
        scores.put("semanticUnderstanding", 0d);
        scores.put("spatialImagination", 0d);
        scores.put("memoryRetrieval", 0d);

        Map<String, Double> den = new LinkedHashMap<>();
        den.put("logicReasoning", 0d);
        den.put("numericCalculation", 0d);
        den.put("semanticUnderstanding", 0d);
        den.put("spatialImagination", 0d);
        den.put("memoryRetrieval", 0d);

        return new StudentDimensionScoresResponse(userId, majorCode, scopeCourse, scores, den, List.of());
    }

    private List<Map<String, Object>> parseJsonArray(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(raw, new TypeReference<>() {});
            return list == null ? List.of() : list;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static double toNumber(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(Objects.requireNonNull(status)).body(Map.of("message", message));
    }
}

