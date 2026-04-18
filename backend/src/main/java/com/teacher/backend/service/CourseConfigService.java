package com.teacher.backend.service;

import com.teacher.backend.dto.CourseConfigDto;
import com.teacher.backend.dto.CourseDimensionWeightsDto;
import com.teacher.backend.entity.CourseDimensionWeights;
import com.teacher.backend.repository.CourseDimensionWeightsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseConfigService {

    public static final double DEFAULT_WEIGHT = 0.2;
    public static final double WEIGHT_MIN = 0.05;
    public static final double WEIGHT_STEP = 0.05;

    private final CourseCatalogService courseCatalogService;
    private final CourseDimensionWeightsRepository weightsRepository;

    public CourseConfigService(
        CourseCatalogService courseCatalogService,
        CourseDimensionWeightsRepository weightsRepository
    ) {
        this.courseCatalogService = courseCatalogService;
        this.weightsRepository = weightsRepository;
    }

    public CourseConfigDto getOrDefaultConfig(String rawCourseName) {
        String courseName = courseCatalogService.normalizeCourseName(rawCourseName);
        CourseDimensionWeights w = ensureDefaultWeights(courseName);
        return toDto(courseName, w);
    }

    @Transactional
    public CourseConfigDto upsertConfig(String rawCourseName, CourseDimensionWeightsDto weights) {
        String courseName = courseCatalogService.normalizeCourseName(rawCourseName);
        CourseDimensionWeights w = ensureDefaultWeights(courseName);

        CourseDimensionWeightsDto normalizedWeights = normalizeAndValidateWeights(weights);
        w.setLogicReasoning(normalizedWeights.logicReasoning());
        w.setNumericCalculation(normalizedWeights.numericCalculation());
        w.setSemanticUnderstanding(normalizedWeights.semanticUnderstanding());
        w.setSpatialImagination(normalizedWeights.spatialImagination());
        w.setMemoryRetrieval(normalizedWeights.memoryRetrieval());
        weightsRepository.save(w);
        return toDto(courseName, w);
    }

    private CourseDimensionWeights ensureDefaultWeights(String courseName) {
        return weightsRepository.findByCourseName(courseName).orElseGet(() -> {
            CourseDimensionWeights w = new CourseDimensionWeights();
            w.setCourseName(courseName);
            w.setLogicReasoning(DEFAULT_WEIGHT);
            w.setNumericCalculation(DEFAULT_WEIGHT);
            w.setSemanticUnderstanding(DEFAULT_WEIGHT);
            w.setSpatialImagination(DEFAULT_WEIGHT);
            w.setMemoryRetrieval(DEFAULT_WEIGHT);
            return weightsRepository.save(w);
        });
    }

    private CourseConfigDto toDto(String courseName, CourseDimensionWeights weights) {
        CourseDimensionWeightsDto w = new CourseDimensionWeightsDto(
            weights == null ? DEFAULT_WEIGHT : weights.getLogicReasoning(),
            weights == null ? DEFAULT_WEIGHT : weights.getNumericCalculation(),
            weights == null ? DEFAULT_WEIGHT : weights.getSemanticUnderstanding(),
            weights == null ? DEFAULT_WEIGHT : weights.getSpatialImagination(),
            weights == null ? DEFAULT_WEIGHT : weights.getMemoryRetrieval()
        );
        return new CourseConfigDto(courseName, w);
    }

    private CourseDimensionWeightsDto normalizeAndValidateWeights(CourseDimensionWeightsDto w) {
        if (w == null) {
            return new CourseDimensionWeightsDto(DEFAULT_WEIGHT, DEFAULT_WEIGHT, DEFAULT_WEIGHT, DEFAULT_WEIGHT, DEFAULT_WEIGHT);
        }
        double lr = normalizeAndValidateWeight(w.logicReasoning());
        double nc = normalizeAndValidateWeight(w.numericCalculation());
        double su = normalizeAndValidateWeight(w.semanticUnderstanding());
        double si = normalizeAndValidateWeight(w.spatialImagination());
        double mr = normalizeAndValidateWeight(w.memoryRetrieval());
        double sum = lr + nc + su + si + mr;
        if (Math.abs(sum - 1.0) > 1e-9) {
            throw new IllegalArgumentException("weights sum must equal 1");
        }
        return new CourseDimensionWeightsDto(lr, nc, su, si, mr);
    }

    private double normalizeAndValidateWeight(double v) {
        if (!Double.isFinite(v)) {
            throw new IllegalArgumentException("weight must be a finite number");
        }
        double rounded = roundToStep(v, WEIGHT_STEP);
        if (rounded < WEIGHT_MIN - 1e-12) {
            throw new IllegalArgumentException("weight must be >= " + WEIGHT_MIN);
        }
        return rounded;
    }

    private static double roundToStep(double v, double step) {
        if (step <= 0) return v;
        return Math.round(v / step) * step;
    }

}
