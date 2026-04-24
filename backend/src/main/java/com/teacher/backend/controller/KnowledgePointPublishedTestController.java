package com.teacher.backend.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teacher.backend.entity.KnowledgePointPublishedTest;
import com.teacher.backend.entity.KnowledgePointTestSubmission;
import com.teacher.backend.entity.StudentState;
import com.teacher.backend.entity.User;
import com.teacher.backend.repository.KnowledgePointPublishedTestRepository;
import com.teacher.backend.repository.KnowledgePointTestSubmissionRepository;
import com.teacher.backend.repository.StudentStateRepository;
import com.teacher.backend.repository.TeacherCoursePermissionRepository;
import com.teacher.backend.repository.UserRepository;
import com.teacher.backend.service.AiService;
import com.teacher.backend.service.CourseCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-point-published-tests")
public class KnowledgePointPublishedTestController {

    private static final Set<String> ALLOWED_TYPES = Set.of("选择题", "填空题");
    private static final int MAX_QUESTIONS = 30;

    private final KnowledgePointPublishedTestRepository testRepository;
    private final KnowledgePointTestSubmissionRepository submissionRepository;
    private final TeacherCoursePermissionRepository permissionRepository;
    private final StudentStateRepository studentStateRepository;
    private final UserRepository userRepository;
    private final CourseCatalogService courseCatalogService;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    public KnowledgePointPublishedTestController(
            KnowledgePointPublishedTestRepository testRepository,
            KnowledgePointTestSubmissionRepository submissionRepository,
            TeacherCoursePermissionRepository permissionRepository,
            StudentStateRepository studentStateRepository,
            UserRepository userRepository,
            CourseCatalogService courseCatalogService,
            AiService aiService,
            ObjectMapper objectMapper) {
        this.testRepository = testRepository;
        this.submissionRepository = submissionRepository;
        this.permissionRepository = permissionRepository;
        this.studentStateRepository = studentStateRepository;
        this.userRepository = userRepository;
        this.courseCatalogService = courseCatalogService;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    /** 教师保存/覆盖发布（单选题 + 填空题） */
    @PostMapping
    @Transactional
    public ResponseEntity<?> save(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return error(HttpStatus.BAD_REQUEST, "body 必填");
        }
        Long teacherUserId = toLong(body.get("teacherUserId"));
        if (teacherUserId == null) {
            return error(HttpStatus.BAD_REQUEST, "teacherUserId 必填");
        }
        User teacher = userRepository.findById(teacherUserId).orElse(null);
        if (teacher == null || (!"teacher".equals(teacher.getRole()) && !"admin".equals(teacher.getRole()))) {
            return error(HttpStatus.FORBIDDEN, "仅教师或管理员可发布");
        }
        String cn = courseCatalogService.normalizeCourseName(String.valueOf(body.getOrDefault("courseName", "")));
        String pn = String.valueOf(body.getOrDefault("pointName", "")).trim();
        if (!StringUtils.hasText(cn) || !StringUtils.hasText(pn)) {
            return error(HttpStatus.BAD_REQUEST, "courseName 与 pointName 不能为空");
        }
        if (!"admin".equals(teacher.getRole())
                && !permissionRepository.existsByTeacherIdAndCourseName(teacherUserId, cn)) {
            return error(HttpStatus.FORBIDDEN, "无该课程权限");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawQuestions = (List<Map<String, Object>>) body.get("questions");
        if (rawQuestions == null || rawQuestions.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "至少一题");
        }
        if (rawQuestions.size() > MAX_QUESTIONS) {
            return error(HttpStatus.BAD_REQUEST, "题目数量不超过 " + MAX_QUESTIONS);
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        int idx = 0;
        for (Map<String, Object> q : rawQuestions) {
            idx++;
            if (q == null) {
                return error(HttpStatus.BAD_REQUEST, "第 " + idx + " 题数据无效");
            }
            String qt = normalizeQuestionType(String.valueOf(q.getOrDefault("question_type", "")));
            if (!ALLOWED_TYPES.contains(qt)) {
                return error(HttpStatus.BAD_REQUEST, "第 " + idx + " 题仅支持 选择题 或 填空题");
            }
            String stem = String.valueOf(q.getOrDefault("question", "")).trim();
            if (!StringUtils.hasText(stem)) {
                return error(HttpStatus.BAD_REQUEST, "第 " + idx + " 题题干不能为空");
            }
            String answer = String.valueOf(q.getOrDefault("answer", "")).trim();
            if (!StringUtils.hasText(answer)) {
                return error(HttpStatus.BAD_REQUEST, "第 " + idx + " 题答案不能为空");
            }
            int fullScore = parsePositiveInt(q.get("fullScore"), 10);
            if (fullScore < 1 || fullScore > 100) {
                return error(HttpStatus.BAD_REQUEST, "第 " + idx + " 题分值需在 1～100");
            }
            @SuppressWarnings("unchecked")
            List<String> options = (List<String>) q.get("options");
            if ("选择题".equals(qt)) {
                if (options == null || options.size() < 2) {
                    return error(HttpStatus.BAD_REQUEST, "第 " + idx + " 题选择题至少 2 个选项");
                }
            } else {
                options = List.of();
            }
            String explanation = String.valueOf(q.getOrDefault("explanation", "")).trim();
            String focusPoint = String.valueOf(q.getOrDefault("focusPointName", "")).trim();
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("question_type", qt);
            one.put("question", stem);
            one.put("options", options == null ? List.of() : options);
            one.put("answer", answer);
            one.put("explanation", explanation);
            one.put("fullScore", fullScore);
            if (StringUtils.hasText(focusPoint)) {
                one.put("focusPointName", focusPoint);
            }
            normalized.add(one);
        }

        String title = String.valueOf(body.getOrDefault("title", "")).trim();
        if (!StringUtils.hasText(title)) {
            title = pn + " · 教师测试";
        }

        Optional<KnowledgePointPublishedTest> existing = testRepository.findByCourseNameAndPointName(cn, pn);
        KnowledgePointPublishedTest entity = existing.orElseGet(KnowledgePointPublishedTest::new);
        entity.setTeacherUserId(teacherUserId);
        entity.setCourseName(cn);
        entity.setPointName(pn);
        entity.setTitle(title);
        try {
            entity.setQuestionsJson(objectMapper.writeValueAsString(normalized));
        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST, "题目序列化失败");
        }
        entity = testRepository.save(entity);
        return ResponseEntity.ok(Map.of(
            "message", "ok",
            "id", entity.getId(),
            "questionCount", normalized.size()
        ));
    }

    /** 学生可见：无答案与解析 */
    @GetMapping("/for-student")
    @Transactional(readOnly = true)
    public ResponseEntity<?> forStudent(
            @RequestParam String courseName,
            @RequestParam String pointName,
            @RequestParam Long userId
    ) {
        if (userId == null) {
            return error(HttpStatus.BAD_REQUEST, "userId 必填");
        }
        User st = userRepository.findById(userId).orElse(null);
        if (st == null || !"student".equals(st.getRole())) {
            return error(HttpStatus.FORBIDDEN, "仅学生可查看");
        }
        String cn = courseCatalogService.normalizeCourseName(courseName);
        String pn = String.valueOf(pointName == null ? "" : pointName).trim();
        if (!StringUtils.hasText(cn) || !StringUtils.hasText(pn)) {
            return error(HttpStatus.BAD_REQUEST, "courseName 与 pointName 不能为空");
        }
        if (!studentJoinedCourse(userId, cn)) {
            return error(HttpStatus.FORBIDDEN, "未加入该课程");
        }
        Optional<KnowledgePointPublishedTest> opt = testRepository.findByCourseNameAndPointName(cn, pn);
        if (opt.isEmpty()) {
            return okWithTest(null);
        }
        KnowledgePointPublishedTest t = opt.get();
        List<Map<String, Object>> stripped = stripAnswers(parseQuestions(t.getQuestionsJson()));
        Map<String, Object> test = new LinkedHashMap<>();
        test.put("id", t.getId());
        test.put("title", t.getTitle());
        test.put("updatedAt", t.getUpdatedAt() == null ? null : t.getUpdatedAt().toString());
        test.put("questions", stripped);
        return okWithTest(test);
    }

    /** 教师可见：含答案 */
    @GetMapping("/for-teacher")
    @Transactional(readOnly = true)
    public ResponseEntity<?> forTeacher(
            @RequestParam String courseName,
            @RequestParam String pointName,
            @RequestParam Long teacherUserId
    ) {
        if (teacherUserId == null) {
            return error(HttpStatus.BAD_REQUEST, "teacherUserId 必填");
        }
        User teacher = userRepository.findById(teacherUserId).orElse(null);
        if (teacher == null || (!"teacher".equals(teacher.getRole()) && !"admin".equals(teacher.getRole()))) {
            return error(HttpStatus.FORBIDDEN, "仅教师或管理员");
        }
        String cn = courseCatalogService.normalizeCourseName(courseName);
        String pn = String.valueOf(pointName == null ? "" : pointName).trim();
        if (!StringUtils.hasText(cn) || !StringUtils.hasText(pn)) {
            return error(HttpStatus.BAD_REQUEST, "courseName 与 pointName 不能为空");
        }
        if (!"admin".equals(teacher.getRole())
                && !permissionRepository.existsByTeacherIdAndCourseName(teacherUserId, cn)) {
            return error(HttpStatus.FORBIDDEN, "无该课程权限");
        }
        Optional<KnowledgePointPublishedTest> opt = testRepository.findByCourseNameAndPointName(cn, pn);
        if (opt.isEmpty()) {
            return okWithTest(null);
        }
        KnowledgePointPublishedTest t = opt.get();
        Map<String, Object> test = new LinkedHashMap<>();
        test.put("id", t.getId());
        test.put("title", t.getTitle());
        test.put("updatedAt", t.getUpdatedAt() == null ? null : t.getUpdatedAt().toString());
        test.put("questions", parseQuestions(t.getQuestionsJson()));
        return okWithTest(test);
    }

    /** JSON 中允许 test 为 null，勿用 Map.of（禁止 null 值会抛异常导致 500） */
    private static ResponseEntity<Map<String, Object>> okWithTest(Object testPayload) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("test", testPayload);
        return ResponseEntity.ok(body);
    }

    /** 学生提交作答 */
    @PostMapping("/submit")
    @Transactional
    public ResponseEntity<?> submit(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return error(HttpStatus.BAD_REQUEST, "body 必填");
        }
        Long userId = toLong(body.get("userId"));
        Long testId = toLong(body.get("testId"));
        if (userId == null || testId == null) {
            return error(HttpStatus.BAD_REQUEST, "userId、testId 必填");
        }
        User st = userRepository.findById(userId).orElse(null);
        if (st == null || !"student".equals(st.getRole())) {
            return error(HttpStatus.FORBIDDEN, "仅学生可提交");
        }
        @SuppressWarnings("unchecked")
        List<String> answers = (List<String>) body.get("answers");
        if (answers == null) {
            return error(HttpStatus.BAD_REQUEST, "answers 必填");
        }

        KnowledgePointPublishedTest t = testRepository.findById(testId).orElse(null);
        if (t == null) {
            return error(HttpStatus.NOT_FOUND, "测试不存在");
        }
        if (!studentJoinedCourse(userId, t.getCourseName())) {
            return error(HttpStatus.FORBIDDEN, "未加入该课程");
        }
        // 一次性提交：已提交则只读
        if (submissionRepository.existsByTestIdAndStudentUserId(testId, userId)) {
            // 返回已提交结果，便于前端直接展示只读
            var existing = submissionRepository.findByTestIdAndStudentUserId(testId, userId).orElse(null);
            if (existing != null) {
                try {
                    List<Map<String, Object>> per = objectMapper.readValue(
                            existing.getPerQuestionJson() == null ? "[]" : existing.getPerQuestionJson(),
                            new TypeReference<>() {});
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("testId", existing.getTestId());
                    out.put("title", t.getTitle());
                    out.put("courseName", existing.getCourseName());
                    out.put("pointName", existing.getPointName());
                    out.put("totalScore", existing.getTotalScore());
                    out.put("fullScore", existing.getFullScore());
                    out.put("perQuestion", per == null ? List.of() : per);
                    out.put("alreadySubmitted", true);
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(out);
                } catch (Exception ignored) {
                    return error(HttpStatus.CONFLICT, "已提交（结果解析失败）");
                }
            }
            return error(HttpStatus.CONFLICT, "已提交");
        }

        List<Map<String, Object>> questions = parseQuestions(t.getQuestionsJson());
        if (questions.size() != answers.size()) {
            return error(HttpStatus.BAD_REQUEST, "作答数量与题目不一致");
        }

        List<Map<String, Object>> perQuestion = new ArrayList<>();
        int totalScore = 0;
        int fullTotal = 0;
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> q = questions.get(i);
            String qt = normalizeQuestionType(String.valueOf(q.getOrDefault("question_type", "")));
            int fs = parsePositiveInt(q.get("fullScore"), 10);
            fullTotal += fs;
            String ref = String.valueOf(q.getOrDefault("answer", ""));
            String studentAns = i < answers.size() && answers.get(i) != null ? String.valueOf(answers.get(i)) : "";
            Map<String, Object> gr = aiService.gradeAnswer(
                    String.valueOf(q.getOrDefault("question", "")),
                    ref,
                    studentAns,
                    qt,
                    null,
                    null,
                    fs
            );
            int sc = toInt(gr.get("score"));
            totalScore += sc;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", i + 1);
            row.put("question_type", qt);
            row.put("question", String.valueOf(q.getOrDefault("question", "")));
            // 保留学生当次答案，便于只读回放（一次性提交）
            row.put("studentAnswer", studentAns);
            row.put(
                    "focusPointName",
                    String.valueOf(q.getOrDefault("focusPointName", "")).trim());
            row.put("score", sc);
            row.put("full_score", fs);
            row.put("explanation", String.valueOf(q.getOrDefault("explanation", "")));
            row.put("summary", String.valueOf(gr.getOrDefault("summary", "")));
            perQuestion.add(row);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("testId", t.getId());
        out.put("title", t.getTitle());
        out.put("courseName", t.getCourseName());
        out.put("pointName", t.getPointName());
        out.put("totalScore", totalScore);
        out.put("fullScore", fullTotal);
        out.put("perQuestion", perQuestion);
        out.put("alreadySubmitted", false);

        try {
            com.teacher.backend.entity.KnowledgePointTestSubmission sub = new com.teacher.backend.entity.KnowledgePointTestSubmission();
            sub.setTestId(t.getId());
            sub.setCourseName(t.getCourseName());
            sub.setPointName(t.getPointName());
            sub.setStudentUserId(userId);
            sub.setSubmittedAt(java.time.Instant.now());
            sub.setTotalScore(totalScore);
            sub.setFullScore(fullTotal);
            sub.setPerQuestionJson(objectMapper.writeValueAsString(perQuestion));
            submissionRepository.save(sub);
        } catch (Exception ex) {
            // 落库失败不应影响判分结果返回，但需提示前端以免“已提交”状态不一致
            out.put("persistError", ex.getMessage());
        }
        return ResponseEntity.ok(out);
    }

    /** 教师统计：max/min/avg/完成率/每题均分 */
    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public ResponseEntity<?> stats(
            @RequestParam String courseName,
            @RequestParam String pointName,
            @RequestParam Long teacherUserId
    ) {
        if (teacherUserId == null) return error(HttpStatus.BAD_REQUEST, "teacherUserId 必填");
        User teacher = userRepository.findById(teacherUserId).orElse(null);
        if (teacher == null || (!"teacher".equals(teacher.getRole()) && !"admin".equals(teacher.getRole()))) {
            return error(HttpStatus.FORBIDDEN, "仅教师或管理员");
        }
        String cn = courseCatalogService.normalizeCourseName(courseName);
        String pn = String.valueOf(pointName == null ? "" : pointName).trim();
        if (!StringUtils.hasText(cn) || !StringUtils.hasText(pn)) {
            return error(HttpStatus.BAD_REQUEST, "courseName 与 pointName 不能为空");
        }
        if (!"admin".equals(teacher.getRole())
                && !permissionRepository.existsByTeacherIdAndCourseName(teacherUserId, cn)) {
            return error(HttpStatus.FORBIDDEN, "无该课程权限");
        }

        KnowledgePointPublishedTest t = testRepository.findByCourseNameAndPointName(cn, pn).orElse(null);
        if (t == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("testId", null);
            empty.put("courseName", cn);
            empty.put("pointName", pn);
            empty.put("submissions", 0);
            empty.put("eligibleStudents", 0);
            empty.put("completionRate", 0);
            empty.put("max", 0);
            empty.put("min", 0);
            empty.put("avg", 0);
            empty.put("perQuestionAvg", List.of());
            empty.put("highScoreQuestions", List.of());
            empty.put("lowScoreQuestions", List.of());
            return ResponseEntity.ok(empty);
        }

        List<Map<String, Object>> questions = Objects.requireNonNull(parseQuestions(t.getQuestionsJson()));
        int qCount = questions.size();
        List<com.teacher.backend.entity.KnowledgePointTestSubmission> subs = submissionRepository.findByTestId(t.getId());
        if (subs == null) {
            subs = List.of();
        }

        // 可见学生人数（已加入该课）
        int eligible = 0;
        try {
            eligible = studentStateRepository.findUserIdsWithCourseInJoined(cn).size();
        } catch (Exception ignored) {
            eligible = 0;
        }

        int submitted = subs.size();
        int completionRate = eligible <= 0 ? 0 : (int) Math.round((submitted * 100.0) / eligible);

        int max = 0;
        int min = 0;
        double avg = 0;
        if (submitted > 0) {
            max = subs.stream().mapToInt(s -> s.getTotalScore() == null ? 0 : s.getTotalScore()).max().orElse(0);
            min = subs.stream().mapToInt(s -> s.getTotalScore() == null ? 0 : s.getTotalScore()).min().orElse(0);
            avg = subs.stream().mapToInt(s -> s.getTotalScore() == null ? 0 : s.getTotalScore()).average().orElse(0);
        }

        // 每题均分：按 index 对齐（从 perQuestionJson 取 score/full_score）
        double[] sumScores = new double[Math.max(0, qCount)];
        double[] sumFull = new double[Math.max(0, qCount)];
        int[] cnts = new int[Math.max(0, qCount)];

        for (var s : subs) {
            List<Map<String, Object>> per;
            try {
                per = objectMapper.readValue(s.getPerQuestionJson() == null ? "[]" : s.getPerQuestionJson(), new TypeReference<>() {});
            } catch (Exception ex) {
                continue;
            }
            if (per == null) continue;
            for (int i = 0; i < per.size() && i < qCount; i++) {
                Map<String, Object> row = per.get(i);
                double sc = toInt(row.get("score"));
                double fs = toInt(row.get("full_score"));
                sumScores[i] += sc;
                sumFull[i] += fs;
                cnts[i] += 1;
            }
        }

        List<Map<String, Object>> perQuestionAvg = new ArrayList<>();
        for (int i = 0; i < qCount; i++) {
            double avgScore = cnts[i] <= 0 ? 0 : (sumScores[i] / cnts[i]);
            double avgFull = cnts[i] <= 0 ? 0 : (sumFull[i] / cnts[i]);
            double ratio = avgFull <= 0 ? 0 : (avgScore / avgFull);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", i + 1);
            row.put("avgScore", Math.round(avgScore * 100.0) / 100.0);
            row.put("avgFullScore", Math.round(avgFull * 100.0) / 100.0);
            row.put("ratio", Math.round(ratio * 1000.0) / 10.0); // percent with 0.1
            row.put("question_type", String.valueOf(questions.get(i).getOrDefault("question_type", "")));
            row.put("focusPointName", String.valueOf(questions.get(i).getOrDefault("focusPointName", "")).trim());
            perQuestionAvg.add(row);
        }

        // 高低分题（按 ratio 排序取前/后 3）
        List<Map<String, Object>> sorted = perQuestionAvg.stream()
                .sorted((a, b) -> Double.compare(toDouble(b.get("ratio")), toDouble(a.get("ratio"))))
                .toList();
        List<Map<String, Object>> high = sorted.stream().limit(3).toList();
        List<Map<String, Object>> low = sorted.stream()
                .sorted((a, b) -> Double.compare(toDouble(a.get("ratio")), toDouble(b.get("ratio"))))
                .limit(3).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("testId", t.getId());
        out.put("title", t.getTitle());
        out.put("courseName", cn);
        out.put("pointName", pn);
        out.put("eligibleStudents", eligible);
        out.put("submissions", submitted);
        out.put("completionRate", completionRate);
        out.put("max", max);
        out.put("min", min);
        out.put("avg", Math.round(avg * 100.0) / 100.0);
        out.put("perQuestionAvg", perQuestionAvg);
        out.put("highScoreQuestions", high);
        out.put("lowScoreQuestions", low);
        return ResponseEntity.ok(out);
    }

    /**
     * 教师端：某次测试（课程+知识点锚点）下每位学生的作答明细（含每题选了什么/填了什么）。
     */
    @GetMapping("/submissions-detail")
    @Transactional(readOnly = true)
    public ResponseEntity<?> submissionsDetail(
            @RequestParam String courseName,
            @RequestParam String pointName,
            @RequestParam Long teacherUserId
    ) {
        if (teacherUserId == null) {
            return error(HttpStatus.BAD_REQUEST, "teacherUserId 必填");
        }
        User teacher = userRepository.findById(teacherUserId).orElse(null);
        if (teacher == null || (!"teacher".equals(teacher.getRole()) && !"admin".equals(teacher.getRole()))) {
            return error(HttpStatus.FORBIDDEN, "仅教师或管理员");
        }
        String cn = courseCatalogService.normalizeCourseName(courseName);
        String pn = String.valueOf(pointName == null ? "" : pointName).trim();
        if (!StringUtils.hasText(cn) || !StringUtils.hasText(pn)) {
            return error(HttpStatus.BAD_REQUEST, "courseName 与 pointName 不能为空");
        }
        if (!"admin".equals(teacher.getRole())
                && !permissionRepository.existsByTeacherIdAndCourseName(teacherUserId, cn)) {
            return error(HttpStatus.FORBIDDEN, "无该课程权限");
        }

        KnowledgePointPublishedTest t = testRepository.findByCourseNameAndPointName(cn, pn).orElse(null);
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("testId", null);
        empty.put("title", null);
        empty.put("courseName", cn);
        empty.put("pointName", pn);
        empty.put("questionCount", 0);
        empty.put("submissions", List.of());
        if (t == null) {
            return ResponseEntity.ok(empty);
        }

        List<Map<String, Object>> questions = parseQuestions(t.getQuestionsJson());
        int qCount = questions.size();
        List<KnowledgePointTestSubmission> subs = submissionRepository.findByTestId(t.getId());
        if (subs == null) {
            subs = List.of();
        }

        List<Long> userIds = subs.stream().map(KnowledgePointTestSubmission::getStudentUserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User u : userRepository.findAllById(userIds)) {
                if (u != null && u.getId() != null) {
                    userMap.put(u.getId(), u);
                }
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (KnowledgePointTestSubmission s : subs) {
            List<Map<String, Object>> per;
            try {
                per = objectMapper.readValue(s.getPerQuestionJson() == null ? "[]" : s.getPerQuestionJson(), new TypeReference<>() {});
            } catch (Exception ex) {
                per = List.of();
            }
            if (per == null) {
                per = List.of();
            }
            List<String> answersByIndex = new ArrayList<>();
            for (int i = 0; i < qCount; i++) {
                answersByIndex.add("");
            }
            for (Map<String, Object> cell : per) {
                int idx0 = toInt(cell.get("index")) - 1;
                if (idx0 >= 0 && idx0 < qCount) {
                    Object sa = cell.get("studentAnswer");
                    answersByIndex.set(idx0, sa == null ? "" : String.valueOf(sa));
                }
            }
            User u = userMap.get(s.getStudentUserId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentUserId", s.getStudentUserId());
            row.put("username", u == null ? ("user#" + s.getStudentUserId()) : u.getUsername());
            row.put("workId", u == null || u.getWorkId() == null ? "" : u.getWorkId());
            row.put("totalScore", s.getTotalScore());
            row.put("fullScore", s.getFullScore());
            row.put("submittedAt", s.getSubmittedAt() == null ? null : s.getSubmittedAt().toString());
            row.put("answersByIndex", answersByIndex);
            row.put("perQuestion", per);
            rows.add(row);
        }
        rows.sort(Comparator.comparing(r -> String.valueOf(r.getOrDefault("username", "")), String.CASE_INSENSITIVE_ORDER));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("testId", t.getId());
        out.put("title", t.getTitle());
        out.put("courseName", cn);
        out.put("pointName", pn);
        out.put("questionCount", qCount);
        out.put("submissions", rows);
        return ResponseEntity.ok(out);
    }

    /**
     * 教师端：学情分析报告（Markdown + 结构化摘要），含逐题得分率、选择题高频错选与 AI 教学建议。
     */
    @GetMapping("/learning-report")
    @Transactional(readOnly = true)
    public ResponseEntity<?> learningReport(
            @RequestParam String courseName,
            @RequestParam String pointName,
            @RequestParam Long teacherUserId
    ) {
        if (teacherUserId == null) {
            return error(HttpStatus.BAD_REQUEST, "teacherUserId 必填");
        }
        User teacher = userRepository.findById(teacherUserId).orElse(null);
        if (teacher == null || (!"teacher".equals(teacher.getRole()) && !"admin".equals(teacher.getRole()))) {
            return error(HttpStatus.FORBIDDEN, "仅教师或管理员");
        }
        String cn = courseCatalogService.normalizeCourseName(courseName);
        String pn = String.valueOf(pointName == null ? "" : pointName).trim();
        if (!StringUtils.hasText(cn) || !StringUtils.hasText(pn)) {
            return error(HttpStatus.BAD_REQUEST, "courseName 与 pointName 不能为空");
        }
        if (!"admin".equals(teacher.getRole())
                && !permissionRepository.existsByTeacherIdAndCourseName(teacherUserId, cn)) {
            return error(HttpStatus.FORBIDDEN, "无该课程权限");
        }

        String generatedAt = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
        KnowledgePointPublishedTest t = testRepository.findByCourseNameAndPointName(cn, pn).orElse(null);

        Map<String, Object> bare = new LinkedHashMap<>();
        bare.put("testId", null);
        bare.put("title", null);
        bare.put("courseName", cn);
        bare.put("pointName", pn);
        bare.put("generatedAt", generatedAt);
        bare.put("teachingSuggestions", "");
        bare.put("reportMarkdown", "# 学情分析报告\n\n当前知识点下暂无已发布测试。\n");
        bare.put("questions", List.of());
        bare.put("submissionCount", 0);
        bare.put("eligibleStudents", 0);
        bare.put("completionRatePercent", null);
        bare.put("classScoreOverview", null);
        if (t == null) {
            return ResponseEntity.ok(bare);
        }

        List<Map<String, Object>> questions = parseQuestions(t.getQuestionsJson());
        int qCount = questions.size();
        List<KnowledgePointTestSubmission> subs = submissionRepository.findByTestId(t.getId());
        if (subs == null) {
            subs = List.of();
        }
        int submitted = subs.size();

        int eligible = 0;
        try {
            eligible = studentStateRepository.findUserIdsWithCourseInJoined(cn).size();
        } catch (Exception ignored) {
            eligible = 0;
        }

        double[] sumScores = new double[Math.max(0, qCount)];
        double[] sumFull = new double[Math.max(0, qCount)];
        int[] cnts = new int[Math.max(0, qCount)];

        @SuppressWarnings("unchecked")
        List<Map<String, Object>>[] wrongChoiceBuckets = new List[qCount];
        for (int i = 0; i < qCount; i++) {
            wrongChoiceBuckets[i] = new ArrayList<>();
        }

        for (KnowledgePointTestSubmission s : subs) {
            List<Map<String, Object>> per;
            try {
                per = objectMapper.readValue(s.getPerQuestionJson() == null ? "[]" : s.getPerQuestionJson(), new TypeReference<>() {});
            } catch (Exception ex) {
                continue;
            }
            if (per == null) {
                continue;
            }
            for (int i = 0; i < per.size() && i < qCount; i++) {
                Map<String, Object> row = per.get(i);
                double sc = toInt(row.get("score"));
                double fs = toInt(row.get("full_score"));
                sumScores[i] += sc;
                sumFull[i] += fs;
                cnts[i] += 1;

                Map<String, Object> q = questions.get(i);
                String qt = normalizeQuestionType(String.valueOf(q.getOrDefault("question_type", "")));
                if (!"选择题".equals(qt)) {
                    continue;
                }
                int fullOne = toInt(q.get("fullScore"));
                if (fullOne <= 0) {
                    fullOne = toInt(row.get("full_score"));
                }
                if (sc < fullOne) {
                    String letter = aiService.normalizeSingleChoiceLetter(String.valueOf(row.get("studentAnswer")));
                    if (StringUtils.hasText(letter) && letter.length() == 1) {
                        wrongChoiceBuckets[i].add(Map.of("letter", letter));
                    } else {
                        wrongChoiceBuckets[i].add(Map.of("letter", "(无效/未选)"));
                    }
                }
            }
        }

        // 每题「未得满分」学生名单（用于报告逐题展示，替代整卷作答流水）
        List<List<String>> wrongStudentLabels = new ArrayList<>();
        for (int i = 0; i < qCount; i++) {
            wrongStudentLabels.add(new ArrayList<>());
        }
        Map<Long, User> reportUserMap = new HashMap<>();
        List<Long> reportUserIds = subs.stream()
                .map(KnowledgePointTestSubmission::getStudentUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!reportUserIds.isEmpty()) {
            for (User u : userRepository.findAllById(reportUserIds)) {
                if (u != null && u.getId() != null) {
                    reportUserMap.put(u.getId(), u);
                }
            }
        }
        for (KnowledgePointTestSubmission s : subs) {
            List<Map<String, Object>> per;
            try {
                per = objectMapper.readValue(s.getPerQuestionJson() == null ? "[]" : s.getPerQuestionJson(), new TypeReference<>() {});
            } catch (Exception ex) {
                continue;
            }
            if (per == null) {
                continue;
            }
            User u = reportUserMap.get(s.getStudentUserId());
            for (int i = 0; i < qCount; i++) {
                Map<String, Object> row = findPerRowByIndex(per, i + 1);
                if (row == null) {
                    continue;
                }
                int sc = toInt(row.get("score"));
                int fs = toInt(row.get("full_score"));
                if (sc < fs) {
                    wrongStudentLabels.get(i).add(formatStudentLabel(u, s.getStudentUserId()));
                }
            }
        }
        for (int i = 0; i < qCount; i++) {
            List<String> raw = wrongStudentLabels.get(i);
            wrongStudentLabels.set(i, raw.stream()
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toCollection(ArrayList::new)));
        }

        List<Map<String, Object>> questionBlocks = new ArrayList<>();
        List<Map<String, Object>> aiPayloadQuestions = new ArrayList<>();

        for (int i = 0; i < qCount; i++) {
            Map<String, Object> q = questions.get(i);
            String qt = normalizeQuestionType(String.valueOf(q.getOrDefault("question_type", "")));
            double avgScore = cnts[i] <= 0 ? 0 : (sumScores[i] / cnts[i]);
            double avgFull = cnts[i] <= 0 ? 0 : (sumFull[i] / cnts[i]);
            Double scoreRate = avgFull <= 0 ? null : Math.round((avgScore / avgFull) * 1000.0) / 10.0;

            Map<String, Integer> wrongDist = new HashMap<>();
            for (Map<String, Object> w : wrongChoiceBuckets[i]) {
                String letter = String.valueOf(w.get("letter"));
                wrongDist.put(letter, wrongDist.getOrDefault(letter, 0) + 1);
            }
            String topWrong = null;
            int topWrongCnt = 0;
            for (Map.Entry<String, Integer> e : wrongDist.entrySet()) {
                if (e.getValue() > topWrongCnt) {
                    topWrongCnt = e.getValue();
                    topWrong = e.getKey();
                }
            }

            Map<String, Object> qb = new LinkedHashMap<>();
            qb.put("index", i + 1);
            qb.put("question_type", qt);
            qb.put("question", String.valueOf(q.getOrDefault("question", "")));
            qb.put("answer", String.valueOf(q.getOrDefault("answer", "")));
            qb.put("explanation", String.valueOf(q.getOrDefault("explanation", "")));
            qb.put("focusPointName", String.valueOf(q.getOrDefault("focusPointName", "")).trim());
            qb.put("scoreRatePercent", scoreRate);
            qb.put("answeredCount", cnts[i]);
            qb.put("topWrongChoice", "选择题".equals(qt) ? topWrong : null);
            qb.put("topWrongChoiceCount", "选择题".equals(qt) && topWrong != null ? topWrongCnt : 0);
            List<Map<String, Object>> distList = new ArrayList<>();
            wrongDist.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .forEach(e -> distList.add(Map.of("option", e.getKey(), "count", e.getValue())));
            qb.put("wrongChoiceDistribution", "选择题".equals(qt) ? distList : List.of());
            qb.put("wrongStudentNames", wrongStudentLabels.get(i));
            questionBlocks.add(qb);

            Map<String, Object> aiQ = new LinkedHashMap<>();
            aiQ.put("index", i + 1);
            aiQ.put("type", qt);
            aiQ.put("scoreRatePercent", scoreRate);
            aiQ.put("answeredCount", cnts[i]);
            if ("选择题".equals(qt)) {
                aiQ.put("topWrongChoice", topWrong);
                aiQ.put("topWrongChoiceCount", topWrongCnt);
            }
            aiPayloadQuestions.add(aiQ);
        }

        Map<String, Object> aiSummary = new LinkedHashMap<>();
        aiSummary.put("courseName", cn);
        aiSummary.put("pointName", pn);
        aiSummary.put("testTitle", t.getTitle());
        aiSummary.put("eligibleStudents", eligible);
        aiSummary.put("submissionCount", submitted);
        aiSummary.put("completionRatePercent", eligible <= 0 ? null : Math.round((submitted * 1000.0) / eligible) / 10.0);
        if (submitted > 0) {
            int mx = subs.stream().mapToInt(s -> s.getTotalScore() == null ? 0 : s.getTotalScore()).max().orElse(0);
            int mn = subs.stream().mapToInt(s -> s.getTotalScore() == null ? 0 : s.getTotalScore()).min().orElse(0);
            double av = subs.stream().mapToInt(s -> s.getTotalScore() == null ? 0 : s.getTotalScore()).average().orElse(0);
            aiSummary.put("maxScore", mx);
            aiSummary.put("minScore", mn);
            aiSummary.put("avgScore", Math.round(av * 100.0) / 100.0);
        }
        aiSummary.put("questions", aiPayloadQuestions);

        String summaryJson;
        try {
            summaryJson = objectMapper.writeValueAsString(aiSummary);
        } catch (Exception e) {
            summaryJson = "{}";
        }

        String teaching = aiService.publishedTestTeachingSuggestions(summaryJson, teacher.getUsername());

        StringBuilder md = new StringBuilder();
        md.append("# 学情分析报告\n\n");
        md.append("- **课程**：").append(mdPlain(cn)).append("\n");
        md.append("- **知识点锚点**：").append(mdPlain(pn)).append("\n");
        md.append("- **试卷标题**：").append(mdPlain(t.getTitle())).append("\n");
        md.append("- **生成时间**：").append(mdPlain(generatedAt)).append("\n");
        md.append("- **应测人数**（已加入课程）：").append(eligible).append("\n");
        md.append("- **已提交份数**：").append(submitted).append("\n\n");

        md.append("## 班级成绩概览\n\n");
        if (submitted <= 0) {
            md.append("暂无学生提交答卷。\n\n");
        } else {
            int mx = subs.stream().mapToInt(s -> s.getTotalScore() == null ? 0 : s.getTotalScore()).max().orElse(0);
            int mn = subs.stream().mapToInt(s -> s.getTotalScore() == null ? 0 : s.getTotalScore()).min().orElse(0);
            double av = subs.stream().mapToInt(s -> s.getTotalScore() == null ? 0 : s.getTotalScore()).average().orElse(0);
            md.append("- 最高分：").append(mx).append("\n");
            md.append("- 最低分：").append(mn).append("\n");
            md.append("- 平均分：").append(Math.round(av * 100.0) / 100.0).append("\n\n");
        }

        md.append("## AI 教学建议\n\n");
        md.append(teaching).append("\n\n");

        md.append("## 逐题分析\n\n");
        for (Map<String, Object> qb : questionBlocks) {
            int idx = toInt(qb.get("index"));
            String qt = String.valueOf(qb.get("question_type"));
            md.append("### 第 ").append(idx).append(" 题（").append(mdPlain(qt)).append("）\n\n");
            if (qb.get("scoreRatePercent") == null) {
                md.append("- **得分率**：—（尚无答卷）\n");
            } else {
                md.append("- **得分率**：").append(qb.get("scoreRatePercent")).append("%（基于 ")
                        .append(qb.get("answeredCount")).append(" 份有效作答）\n");
            }
            // 勿用 ``` 围栏：围栏内内容会被当作纯文本，前端无法渲染 LaTeX/列表等 Markdown
            md.append("\n**题干**\n\n").append(mdBlockquoteBody(String.valueOf(qb.get("question")))).append("\n");
            md.append("**参考答案**\n\n").append(mdAnswerBody(String.valueOf(qb.get("answer")))).append("\n");
            md.append("**解析**\n\n").append(mdBlockquoteBody(String.valueOf(qb.get("explanation")))).append("\n");
            if ("选择题".equals(qt)) {
                md.append("- **高频错选（在答错样本中）**：");
                if (qb.get("topWrongChoice") == null || Objects.equals(qb.get("topWrongChoiceCount"), 0)) {
                    md.append("无（全部答对或无错选样本）\n\n");
                } else {
                    md.append(mdPlain(String.valueOf(qb.get("topWrongChoice"))))
                            .append("（").append(qb.get("topWrongChoiceCount")).append(" 人次）\n\n");
                    md.append("错选分布：");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> dist = (List<Map<String, Object>>) qb.get("wrongChoiceDistribution");
                    if (dist != null && !dist.isEmpty()) {
                        md.append(dist.stream()
                                .map(d -> mdPlain(String.valueOf(d.get("option"))) + ":" + d.get("count"))
                                .reduce((a, b) -> a + "，" + b)
                                .orElse("—"));
                    } else {
                        md.append("—");
                    }
                    md.append("\n\n");
                }
            }
            @SuppressWarnings("unchecked")
            List<String> wrongNames = (List<String>) qb.get("wrongStudentNames");
            md.append("**未得满分学生**：");
            if (wrongNames == null || wrongNames.isEmpty()) {
                md.append("_无_");
            } else {
                md.append(wrongNames.stream().map(KnowledgePointPublishedTestController::mdPlain).collect(Collectors.joining("、")));
            }
            md.append("\n\n");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("testId", t.getId());
        out.put("title", t.getTitle());
        out.put("courseName", cn);
        out.put("pointName", pn);
        out.put("generatedAt", generatedAt);
        out.put("teachingSuggestions", teaching);
        out.put("reportMarkdown", md.toString());
        out.put("questions", questionBlocks);
        out.put("submissionCount", submitted);
        out.put("eligibleStudents", eligible);
        out.put(
                "completionRatePercent",
                eligible <= 0 ? null : Math.round((submitted * 1000.0) / eligible) / 10.0);
        Map<String, Object> classScoreOverview = new LinkedHashMap<>();
        if (submitted > 0) {
            int mx = subs.stream().mapToInt(s -> s.getTotalScore() == null ? 0 : s.getTotalScore()).max().orElse(0);
            int mn = subs.stream().mapToInt(s -> s.getTotalScore() == null ? 0 : s.getTotalScore()).min().orElse(0);
            double av = subs.stream().mapToInt(s -> s.getTotalScore() == null ? 0 : s.getTotalScore()).average().orElse(0);
            classScoreOverview.put("maxScore", mx);
            classScoreOverview.put("minScore", mn);
            classScoreOverview.put("avgScore", Math.round(av * 100.0) / 100.0);
        }
        out.put("classScoreOverview", submitted > 0 ? classScoreOverview : null);
        return ResponseEntity.ok(out);
    }

    private static Map<String, Object> findPerRowByIndex(List<Map<String, Object>> per, int oneBasedIndex) {
        if (per == null || oneBasedIndex < 1) {
            return null;
        }
        for (Map<String, Object> row : per) {
            if (row != null && toInt(row.get("index")) == oneBasedIndex) {
                return row;
            }
        }
        if (oneBasedIndex <= per.size()) {
            return per.get(oneBasedIndex - 1);
        }
        return null;
    }

    private static String formatStudentLabel(User u, Long studentUserId) {
        if (studentUserId == null) {
            return "?";
        }
        if (u == null) {
            return "user#" + studentUserId;
        }
        String name = u.getUsername() == null ? ("user#" + studentUserId) : u.getUsername();
        String wid = u.getWorkId();
        if (StringUtils.hasText(wid)) {
            return name + "（学号" + wid + "）";
        }
        return name;
    }

    private static String mdPlain(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r\n", "\n").replace("\n", " ").trim();
    }

    /** 多行放入引用块，便于 Markdown 引擎解析行内 $...$、列表等（避免代码围栏导致整段当纯文本）。 */
    private static String mdBlockquoteBody(String s) {
        String t = s == null ? "" : s;
        String norm = t.replace("\r\n", "\n").replace("\r", "\n");
        if (!StringUtils.hasText(norm)) {
            return "> _（空）_\n";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : norm.split("\n", -1)) {
            sb.append("> ").append(line).append("\n");
        }
        return sb.toString();
    }

    /** 参考答案：单行用段落，多行用引用块。 */
    private static String mdAnswerBody(String s) {
        String t = s == null ? "" : s.replace("\r\n", "\n").replace("\r", "\n");
        if (!StringUtils.hasText(t)) {
            return "_（空）_\n\n";
        }
        if (!t.contains("\n")) {
            return t.trim() + "\n\n";
        }
        return mdBlockquoteBody(t);
    }

    /**
     * 教师端：单课数据看板汇总（已发布测试套数、提交人次、各知识点测试概况）。
     * 与 {@link #stats} 口径一致：完成率 = 该课已加入学生数作为分母。
     */
    @GetMapping("/course-summary")
    @Transactional(readOnly = true)
    public ResponseEntity<?> courseSummary(@RequestParam Long teacherUserId, @RequestParam String courseName) {
        if (teacherUserId == null) {
            return error(HttpStatus.BAD_REQUEST, "teacherUserId 必填");
        }
        User teacher = userRepository.findById(teacherUserId).orElse(null);
        if (teacher == null || (!"teacher".equals(teacher.getRole()) && !"admin".equals(teacher.getRole()))) {
            return error(HttpStatus.FORBIDDEN, "仅教师或管理员");
        }
        String cn = courseCatalogService.normalizeCourseName(courseName);
        if (!StringUtils.hasText(cn)) {
            return error(HttpStatus.BAD_REQUEST, "courseName 不能为空");
        }
        if (!"admin".equals(teacher.getRole())
                && !permissionRepository.existsByTeacherIdAndCourseName(teacherUserId, cn)) {
            return error(HttpStatus.FORBIDDEN, "无该课程权限");
        }

        int eligible = 0;
        try {
            eligible = studentStateRepository.findUserIdsWithCourseInJoined(cn).size();
        } catch (Exception ignored) {
            eligible = 0;
        }

        List<KnowledgePointPublishedTest> tests = testRepository.findByCourseName(cn);
        List<Map<String, Object>> testRows = new ArrayList<>();
        for (KnowledgePointPublishedTest t : tests) {
            if (t == null || t.getId() == null) {
                continue;
            }
            List<KnowledgePointTestSubmission> subs = submissionRepository.findByTestId(t.getId());
            if (subs == null) {
                subs = List.of();
            }
            int submitted = subs.size();
            int completionRate = eligible <= 0 ? 0 : (int) Math.round((submitted * 100.0) / eligible);
            double avg = 0;
            if (submitted > 0) {
                avg = subs.stream()
                        .mapToInt(s -> s.getTotalScore() == null ? 0 : s.getTotalScore())
                        .average()
                        .orElse(0);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("testId", t.getId());
            row.put("pointName", t.getPointName());
            row.put("title", t.getTitle());
            row.put("submissions", submitted);
            row.put("completionRate", completionRate);
            row.put("avgScore", Math.round(avg * 100.0) / 100.0);
            testRows.add(row);
        }
        testRows.sort((a, b) -> Integer.compare(toInt(b.get("submissions")), toInt(a.get("submissions"))));

        Double avgAll = submissionRepository.avgTotalScoreByCourseName(cn);
        double courseAvgScore = avgAll == null ? 0 : Math.round(avgAll * 100.0) / 100.0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("courseName", cn);
        out.put("eligibleStudents", eligible);
        out.put("publishedTestCount", tests.size());
        out.put("testSubmissionCount", submissionRepository.countByCourseName(cn));
        out.put("distinctSubmitters", submissionRepository.countDistinctStudentUserIdByCourseName(cn));
        out.put("courseAvgScore", courseAvgScore);
        out.put("tests", testRows);
        return ResponseEntity.ok(out);
    }

    /** 学生查询已提交结果（用于进入页面即只读） */
    @GetMapping("/my-submission")
    @Transactional(readOnly = true)
    public ResponseEntity<?> mySubmission(@RequestParam Long userId, @RequestParam Long testId) {
        if (userId == null || testId == null) {
            return error(HttpStatus.BAD_REQUEST, "userId、testId 必填");
        }
        User st = userRepository.findById(userId).orElse(null);
        if (st == null || !"student".equals(st.getRole())) {
            return error(HttpStatus.FORBIDDEN, "仅学生可查看");
        }
        var sub = submissionRepository.findByTestIdAndStudentUserId(testId, userId).orElse(null);
        if (sub == null) {
            return ResponseEntity.ok(Map.of("submitted", false));
        }
        KnowledgePointPublishedTest t = testRepository.findById(testId).orElse(null);
        List<Map<String, Object>> per = List.of();
        try {
            per = objectMapper.readValue(sub.getPerQuestionJson() == null ? "[]" : sub.getPerQuestionJson(), new TypeReference<>() {});
            if (per == null) per = List.of();
        } catch (Exception ignored) {
            per = List.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("submitted", true);
        out.put("testId", sub.getTestId());
        out.put("title", t == null ? null : t.getTitle());
        out.put("courseName", sub.getCourseName());
        out.put("pointName", sub.getPointName());
        out.put("totalScore", sub.getTotalScore());
        out.put("fullScore", sub.getFullScore());
        out.put("perQuestion", per);
        out.put("submittedAt", sub.getSubmittedAt() == null ? null : sub.getSubmittedAt().toString());
        return ResponseEntity.ok(out);
    }

    /** 教师个人中心：统计其已授权课程内的已发布测试总数（不区分发布者） */
    @GetMapping("/count-by-teacher-courses")
    @Transactional(readOnly = true)
    public ResponseEntity<?> countByTeacherCourses(@RequestParam Long teacherUserId) {
        if (teacherUserId == null) {
            return error(HttpStatus.BAD_REQUEST, "teacherUserId 必填");
        }
        User teacher = userRepository.findById(teacherUserId).orElse(null);
        if (teacher == null || (!"teacher".equals(teacher.getRole()) && !"admin".equals(teacher.getRole()))) {
            return error(HttpStatus.FORBIDDEN, "仅教师或管理员");
        }

        List<String> courses;
        if ("admin".equals(teacher.getRole())) {
            // 管理员口径：全课程（通过测试表反推）
            courses = testRepository.findAll().stream()
                    .map(KnowledgePointPublishedTest::getCourseName)
                    .filter(StringUtils::hasText)
                    .map(courseCatalogService::normalizeCourseName)
                    .distinct()
                    .toList();
        } else {
            courses = permissionRepository.findByTeacherIdOrderByIdAsc(teacherUserId).stream()
                    .map(p -> courseCatalogService.normalizeCourseName(p.getCourseName()))
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }

        HashSet<Long> testIds = new HashSet<>();
        for (String c : courses) {
            for (KnowledgePointPublishedTest t : testRepository.findByCourseName(c)) {
                if (t != null && t.getId() != null) testIds.add(t.getId());
            }
        }
        return ResponseEntity.ok(Map.of(
                "teacherUserId", teacherUserId,
                "courseCount", courses.size(),
                "publishedTestCount", testIds.size()
        ));
    }

    private boolean studentJoinedCourse(Long userId, String courseName) {
        Optional<StudentState> opt = studentStateRepository.findByUserId(userId);
        if (opt.isEmpty()) {
            return false;
        }
        String raw = opt.get().getJoinedCoursesJson();
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        try {
            List<String> list = objectMapper.readValue(raw, new TypeReference<>() {});
            if (list == null) {
                return false;
            }
            String norm = courseCatalogService.normalizeCourseName(courseName);
            for (String c : list) {
                if (norm.equals(courseCatalogService.normalizeCourseName(c))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private List<Map<String, Object>> parseQuestions(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<>() {});
            return list == null ? List.of() : list;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<Map<String, Object>> stripAnswers(List<Map<String, Object>> questions) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> q : questions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("question_type", q.get("question_type"));
            m.put("question", q.get("question"));
            m.put("options", q.get("options"));
            m.put("fullScore", q.get("fullScore"));
            Object fp = q.get("focusPointName");
            if (fp != null && StringUtils.hasText(String.valueOf(fp).trim())) {
                m.put("focusPointName", String.valueOf(fp).trim());
            }
            out.add(m);
        }
        return out;
    }

    private static String normalizeQuestionType(String raw) {
        String s = raw == null ? "" : raw.trim();
        if ("单选题".equals(s)) {
            return "选择题";
        }
        return s;
    }

    private static int parsePositiveInt(Object o, int def) {
        if (o instanceof Number n) {
            return Math.max(1, n.intValue());
        }
        try {
            return Math.max(1, Integer.parseInt(String.valueOf(o)));
        } catch (Exception e) {
            return def;
        }
    }

    private static int toInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return 0;
        }
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(Objects.requireNonNull(status)).body(Map.of("message", message));
    }
}
