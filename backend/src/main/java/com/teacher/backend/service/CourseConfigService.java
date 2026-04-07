package com.teacher.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.teacher.backend.dto.CourseConfigDto;
import com.teacher.backend.dto.CourseCreditRuleDto;
import com.teacher.backend.dto.CourseDimensionWeightsDto;
import com.teacher.backend.entity.CourseCreditRule;
import com.teacher.backend.entity.CourseDimensionWeights;
import com.teacher.backend.repository.CourseCreditRuleRepository;
import com.teacher.backend.repository.CourseDimensionWeightsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CourseConfigService {

    public static final double DEFAULT_WEIGHT = 0.2;
    public static final double WEIGHT_MIN = 0.05;
    public static final double WEIGHT_STEP = 0.05;

    public static final double CREDIT_MIN = 0.5;
    public static final double CREDIT_MAX = 10.0;
    public static final double CREDIT_STEP = 0.5;

    public static final double DEFAULT_CREDIT = 0.5;

    private final CourseCatalogService courseCatalogService;
    private final CourseDimensionWeightsRepository weightsRepository;
    private final CourseCreditRuleRepository creditRuleRepository;
    private final MajorPathLookupService majorPathLookupService;

    public CourseConfigService(
        CourseCatalogService courseCatalogService,
        CourseDimensionWeightsRepository weightsRepository,
        CourseCreditRuleRepository creditRuleRepository,
        MajorPathLookupService majorPathLookupService
    ) {
        this.courseCatalogService = courseCatalogService;
        this.weightsRepository = weightsRepository;
        this.creditRuleRepository = creditRuleRepository;
        this.majorPathLookupService = majorPathLookupService;
    }

    public CourseConfigDto getOrDefaultConfig(String rawCourseName) {
        String courseName = courseCatalogService.normalizeCourseName(rawCourseName);
        CourseDimensionWeights w = ensureDefaultWeights(courseName);
        List<CourseCreditRule> rules = creditRuleRepository.findByCourseNameOrderByIdAsc(courseName);
        return toDto(courseName, w, rules);
    }

    public List<CourseConfigDto> listAllConfigs() {
        List<String> courses = courseCatalogService.allCourses();
        List<CourseConfigDto> out = new ArrayList<>();
        for (String c : courses) {
            out.add(getOrDefaultConfig(c));
        }
        return out;
    }

    @Transactional
    public CourseConfigDto upsertConfig(String rawCourseName, CourseDimensionWeightsDto weights, List<CourseCreditRuleDto> creditRules) {
        String courseName = courseCatalogService.normalizeCourseName(rawCourseName);
        CourseDimensionWeights w = ensureDefaultWeights(courseName);

        CourseDimensionWeightsDto normalizedWeights = normalizeAndValidateWeights(weights);
        w.setLogicReasoning(normalizedWeights.logicReasoning());
        w.setNumericCalculation(normalizedWeights.numericCalculation());
        w.setSemanticUnderstanding(normalizedWeights.semanticUnderstanding());
        w.setSpatialImagination(normalizedWeights.spatialImagination());
        w.setMemoryRetrieval(normalizedWeights.memoryRetrieval());
        weightsRepository.save(w);

        creditRuleRepository.deleteByCourseName(courseName);
        List<CourseCreditRule> savedRules = new ArrayList<>();
        if (creditRules != null) {
            for (CourseCreditRuleDto dto : creditRules) {
                List<String> codes = normalizeMajorCodes(dto.majorCodes());
                if (codes.isEmpty()) {
                    continue;
                }
                CourseCreditRule rule = new CourseCreditRule();
                rule.setCourseName(courseName);
                rule.setMajorCodes(codes);
                rule.setCredit(normalizeAndValidateCredit(dto.credit()));
                savedRules.add(creditRuleRepository.save(rule));
            }
        }

        return toDto(courseName, w, savedRules);
    }

    /**
     * 学生专业路径上自最深向浅查找：若某条规则包含该层级的 code，则使用该规则的学分；否则 0.5。
     * 规则按保存顺序，同深度多条命中时取先保存的规则。
     */
    public double resolveCreditForStudentMajor(String rawCourseName, String studentMajorCode) {
        String courseName = courseCatalogService.normalizeCourseName(rawCourseName);
        List<CourseCreditRule> rules = creditRuleRepository.findByCourseNameOrderByIdAsc(courseName);
        if (rules == null || rules.isEmpty()) {
            return DEFAULT_CREDIT;
        }

        List<String> path = majorPathLookupService.resolvePathCodes(studentMajorCode);
        if (path == null || path.isEmpty()) {
            return DEFAULT_CREDIT;
        }

        for (int i = path.size() - 1; i >= 0; i--) {
            String code = trimOrNull(path.get(i));
            if (!StringUtils.hasText(code)) {
                continue;
            }
            for (CourseCreditRule rule : rules) {
                if (ruleContainsCode(rule.getMajorCodes(), code)) {
                    return rule.getCredit();
                }
            }
        }

        return DEFAULT_CREDIT;
    }

    private static boolean ruleContainsCode(List<String> majorCodes, String code) {
        if (majorCodes == null || majorCodes.isEmpty()) {
            return false;
        }
        for (String c : majorCodes) {
            if (Objects.equals(trimOrNull(c), code)) {
                return true;
            }
        }
        return false;
    }

    private List<String> normalizeMajorCodes(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> set = new LinkedHashSet<>();
        for (String s : raw) {
            String t = trimOrNull(s);
            if (StringUtils.hasText(t)) {
                set.add(t);
            }
        }
        return new ArrayList<>(set);
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

    private CourseConfigDto toDto(String courseName, CourseDimensionWeights weights, List<CourseCreditRule> rules) {
        CourseDimensionWeightsDto w = new CourseDimensionWeightsDto(
            weights == null ? DEFAULT_WEIGHT : weights.getLogicReasoning(),
            weights == null ? DEFAULT_WEIGHT : weights.getNumericCalculation(),
            weights == null ? DEFAULT_WEIGHT : weights.getSemanticUnderstanding(),
            weights == null ? DEFAULT_WEIGHT : weights.getSpatialImagination(),
            weights == null ? DEFAULT_WEIGHT : weights.getMemoryRetrieval()
        );

        List<CourseCreditRuleDto> outRules = (rules == null ? List.<CourseCreditRuleDto>of() : rules.stream().map(r ->
            new CourseCreditRuleDto(r.getCredit(), r.getMajorCodes() == null ? List.of() : List.copyOf(r.getMajorCodes()))
        ).toList());
        return new CourseConfigDto(courseName, w, outRules);
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

    private double normalizeAndValidateCredit(double v) {
        if (!Double.isFinite(v)) {
            throw new IllegalArgumentException("credit must be a finite number");
        }
        double rounded = roundToStep(v, CREDIT_STEP);
        if (rounded < CREDIT_MIN - 1e-12 || rounded > CREDIT_MAX + 1e-12) {
            throw new IllegalArgumentException("credit must be between " + CREDIT_MIN + " and " + CREDIT_MAX);
        }
        return rounded;
    }

    private static double roundToStep(double v, double step) {
        if (step <= 0) return v;
        return Math.round(v / step) * step;
    }

    private static String trimOrNull(String s) {
        if (!StringUtils.hasText(s)) return null;
        return s.trim();
    }
}
