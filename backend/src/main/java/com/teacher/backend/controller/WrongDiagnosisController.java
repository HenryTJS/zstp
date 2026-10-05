package com.teacher.backend.controller;

import java.util.List;
import java.util.Map;

import com.teacher.backend.dto.WrongDiagnosisRequest;
import com.teacher.backend.service.WrongDiagnosisService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 错题诊断接口：支撑「错题 → 错因 → 复习资料 → 针对性练习」的个性化干预闭环。
 */
@RestController
@RequestMapping("/api")
public class WrongDiagnosisController {

    private static final Logger log = LoggerFactory.getLogger(WrongDiagnosisController.class);

    private final WrongDiagnosisService wrongDiagnosisService;

    public WrongDiagnosisController(WrongDiagnosisService wrongDiagnosisService) {
        this.wrongDiagnosisService = wrongDiagnosisService;
    }

    @PostMapping("/wrong-diagnosis")
    public ResponseEntity<?> diagnose(@RequestBody(required = false) WrongDiagnosisRequest request) {
        String courseName = request == null ? null : request.courseName();
        String knowledgePoint = request == null ? null : request.knowledgePoint();
        List<Map<String, Object>> items = request == null ? null : request.wrongItems();

        log.info("Hit /api/wrong-diagnosis, courseName={}, knowledgePoint={}, items={}",
            courseName, knowledgePoint, items == null ? 0 : items.size());

        if (!StringUtils.hasText(knowledgePoint)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "knowledgePoint 必填"));
        }

        return ResponseEntity.ok(wrongDiagnosisService.diagnose(courseName, knowledgePoint, items));
    }
}
