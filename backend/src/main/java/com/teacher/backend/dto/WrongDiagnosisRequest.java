package com.teacher.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * 错题诊断请求。
 *
 * <p>wrongItems 直接复用前端错题本中的条目（结构由前端定义，后端不校验），
 * 其中会读取这些字段：question / options / myAnswer / answer /
 * studentAnswerRaw / referenceAnswerRaw / explanation / knowledgePoint。
 */
public record WrongDiagnosisRequest(
    Long userId,
    String courseName,
    String knowledgePoint,
    List<Map<String, Object>> wrongItems
) {}
