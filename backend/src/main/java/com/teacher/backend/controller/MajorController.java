package com.teacher.backend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import java.io.File;
import java.util.*;

@RestController
@RequestMapping("/api/majors")
public class MajorController {
    @GetMapping("/tree")
    public List<Map<String, Object>> getMajorTree(@RequestParam(defaultValue = "1") int level,
                                                  @RequestParam(required = false) String parentCode) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        File jsonFile = new File("bcmr.json");
        List<Map<String, Object>> majors = mapper.readValue(jsonFile, new TypeReference<>() {});
        // 将原始的 number 字段映射为 code，前端期望使用 code 字段
        majors = majors.stream().map(this::mapNumberToCodeRecursive).toList();
        if (level == 1) {
            return majors;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        findSubfields(majors, parentCode, level, 1, result);
        return result;
    }

    private void findSubfields(List<Map<String, Object>> majors, String parentCode, int targetLevel, int currentLevel, List<Map<String, Object>> result) {
        for (Map<String, Object> major : majors) {
            String code = (String) major.get("code");
            Object subfieldsObj = major.get("subfields");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> subfields = (subfieldsObj instanceof List) ? (List<Map<String, Object>>) subfieldsObj : null;
            if (currentLevel == targetLevel - 1 && Objects.equals(code, parentCode)) {
                if (subfields != null) result.addAll(subfields);
            } else if (subfields != null) {
                findSubfields(subfields, parentCode, targetLevel, currentLevel + 1, result);
            }
        }
    }

    private Map<String, Object> mapNumberToCodeRecursive(Map<String, Object> src) {
        Map<String, Object> dst = new LinkedHashMap<>();
        // copy name
        dst.put("name", src.get("name"));
        // map number -> code
        if (src.containsKey("number")) {
            dst.put("code", src.get("number"));
        }
        // copy other fields if present
        if (src.containsKey("subfields")) {
            Object sf = src.get("subfields");
            if (sf instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> list = (List<Map<String, Object>>) sf;
                List<Map<String, Object>> mapped = list.stream().map(this::mapNumberToCodeRecursive).toList();
                dst.put("subfields", mapped);
            } else {
                dst.put("subfields", sf);
            }
        }
        return dst;
    }
}
