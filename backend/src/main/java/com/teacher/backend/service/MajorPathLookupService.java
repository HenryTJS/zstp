package com.teacher.backend.service;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 根据 bcmr.json 专业树，将叶子或中间节点的 code 解析为从门类到具体方向的三级名称路径。
 */
@Service
public class MajorPathLookupService {

    private List<Map<String, Object>> tree = List.of();

    @PostConstruct
    void load() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> raw = null;
            ClassPathResource cp = new ClassPathResource("bcmr.json");
            if (cp.exists()) {
                try (InputStream in = cp.getInputStream()) {
                    raw = mapper.readValue(in, new TypeReference<>() {});
                }
            }
            if (raw == null) {
                File f = new File("bcmr.json");
                if (f.exists()) {
                    raw = mapper.readValue(f, new TypeReference<>() {});
                }
            }
            if (raw == null) {
                return;
            }
            tree = raw.stream().map(this::mapNumberToCodeRecursive).toList();
        } catch (Exception e) {
            tree = List.of();
        }
    }

    /**
     * @return 最多 3 个名称：门类、专业类、专业；若只选到一级或二级则列表较短。
     */
    public List<String> resolvePathNames(String code) {
        if (!StringUtils.hasText(code) || tree.isEmpty()) {
            return List.of();
        }
        String trimmed = code.trim();
        Optional<List<String>> path = findPathRecursive(tree, trimmed, List.of());
        return path.orElse(List.of());
    }

    /**
     * @return 最多 3 个 code：一级、二级、三级；若只选到一级或二级则列表较短。
     */
    public List<String> resolvePathCodes(String code) {
        if (!StringUtils.hasText(code) || tree.isEmpty()) {
            return List.of();
        }
        String trimmed = code.trim();
        Optional<List<String>> path = findCodePathRecursive(tree, trimmed, List.of());
        return path.orElse(List.of());
    }

    private Optional<List<String>> findPathRecursive(
        List<Map<String, Object>> nodes,
        String targetCode,
        List<String> ancestorNames
    ) {
        if (nodes == null || nodes.isEmpty()) {
            return Optional.empty();
        }
        for (Map<String, Object> node : nodes) {
            String c = node.get("code") == null ? null : String.valueOf(node.get("code"));
            String name = node.get("name") == null ? "" : String.valueOf(node.get("name"));
            List<String> here = new ArrayList<>(ancestorNames);
            here.add(name);
            if (Objects.equals(trimmedCode(c), trimmedCode(targetCode))) {
                return Optional.of(here);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sub = (List<Map<String, Object>>) node.get("subfields");
            if (sub != null && !sub.isEmpty()) {
                Optional<List<String>> deeper = findPathRecursive(sub, targetCode, here);
                if (deeper.isPresent()) {
                    return deeper;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<List<String>> findCodePathRecursive(
        List<Map<String, Object>> nodes,
        String targetCode,
        List<String> ancestorCodes
    ) {
        if (nodes == null || nodes.isEmpty()) {
            return Optional.empty();
        }
        for (Map<String, Object> node : nodes) {
            String c = node.get("code") == null ? null : String.valueOf(node.get("code"));
            List<String> here = new ArrayList<>(ancestorCodes);
            if (StringUtils.hasText(c)) {
                here.add(c.trim());
            }
            if (Objects.equals(trimmedCode(c), trimmedCode(targetCode))) {
                return Optional.of(here);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sub = (List<Map<String, Object>>) node.get("subfields");
            if (sub != null && !sub.isEmpty()) {
                Optional<List<String>> deeper = findCodePathRecursive(sub, targetCode, here);
                if (deeper.isPresent()) {
                    return deeper;
                }
            }
        }
        return Optional.empty();
    }

    private static String trimmedCode(String c) {
        return c == null ? "" : c.trim();
    }

    private Map<String, Object> mapNumberToCodeRecursive(Map<String, Object> src) {
        Map<String, Object> dst = new LinkedHashMap<>();
        dst.put("name", src.get("name"));
        if (src.containsKey("number")) {
            dst.put("code", src.get("number"));
        }
        if (src.containsKey("subfields")) {
            Object sf = src.get("subfields");
            if (sf instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> cast = (List<Map<String, Object>>) (List<?>) list;
                dst.put("subfields", cast.stream().map(this::mapNumberToCodeRecursive).toList());
            } else {
                dst.put("subfields", sf);
            }
        }
        return dst;
    }
}
