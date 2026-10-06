package com.teacher.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * AI 问答服务。
 *
 * <p>相比通用聊天，这里会先检索「课程 + 知识点」对应的课件文本（见 {@link MaterialTextService}），
 * 把资料内容作为回答依据注入 prompt，并要求 AI 列出「参考资料」，
 * 使助手从通用聊天机器人变成"读过课程资料的助教"。
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    /** 注入 prompt 的资料文本总字符上限（约 2 万 token 量级，兼顾效果与响应速度）。 */
    private static final int MAX_CONTEXT_CHARS = 60000;

    private final AiClient aiClient;
    private final MaterialTextService materialTextService;

    public AiChatService(AiClient aiClient, MaterialTextService materialTextService) {
        this.aiClient = aiClient;
        this.materialTextService = materialTextService;
    }

    /**
     * 回答用户问题。若提供了课程与知识点，会优先基于该知识点下的课件资料作答。
     */
    public Map<String, Object> agentChat(String question, String role, String username, String courseName, String knowledgePoint) {
        String q = safe(question);
        String r = safe(role);
        String u = safe(username);
        String cn = safe(courseName);
        String kp = safe(knowledgePoint);

        if (!StringUtils.hasText(q)) {
            return Map.of("answer", "请先输入你的问题。");
        }

        if (!aiClient.aiEnabled()) {
            return Map.of("answer", "AI 助手暂未配置（OPENAI_API_KEY 为空），无法回答你的问题。请先联系管理员配置后再试。");
        }

        Map<String, Object> context = loadCourseContext(cn, kp);
        boolean hasText = Boolean.TRUE.equals(context.get("hasText"));

        String systemPrompt = buildSystemPrompt(r, u, hasText);
        String userPrompt = buildUserPrompt(q, cn, kp, context);

        try {
            Map<String, Object> result = aiClient.chatJson(systemPrompt, userPrompt);
            String answer = String.valueOf(result.getOrDefault("answer", ""));
            if (!StringUtils.hasText(answer)) {
                answer = "抱歉，我暂时无法回答这个问题，请换个方式再问一次。";
            }

            List<String> citations = toStringList(result.get("citations"));
            if (citations.isEmpty() && hasText) {
                // AI 未给出处时，用实际注入的资料标题兜底，避免前端展示不到参考来源
                citations = documentTitles(context);
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("answer", answer);
            out.put("citations", citations);
            out.put("usedMaterials", toUsedMaterials(context));
            out.put("hasCourseContext", hasText);
            return out;
        } catch (Exception exception) {
            log.warn("agentChat failed: {}", exception.getMessage());
            return Map.of("answer", "AI 服务暂时不可用，请稍后重试。");
        }
    }

    /**
     * 检索课程资料。未提供课程/知识点或检索失败时返回空上下文，不影响通用问答。
     */
    private Map<String, Object> loadCourseContext(String courseName, String knowledgePoint) {
        if (!StringUtils.hasText(courseName) || !StringUtils.hasText(knowledgePoint)) {
            return emptyContext();
        }
        try {
            return materialTextService.buildContext(courseName, knowledgePoint, MAX_CONTEXT_CHARS);
        } catch (Exception exception) {
            log.warn("loadCourseContext failed: {}", exception.getMessage());
            return emptyContext();
        }
    }

    private static Map<String, Object> emptyContext() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("docs", List.of());
        out.put("imageOnlyTitles", List.of());
        out.put("totalChars", 0);
        out.put("hasText", false);
        return out;
    }

    private String buildSystemPrompt(String role, String username, boolean hasCourseContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是智能学习助手，名叫「智学小T」。")
            .append("你正在帮助一位").append(StringUtils.hasText(role) ? role : "学生").append("解答学习问题。");
        if (StringUtils.hasText(username)) {
            prompt.append("对方用户名：").append(username).append("。");
        }
        prompt.append("请用简体中文、友好且专业的语气回答。");

        if (hasCourseContext) {
            prompt.append("本轮问题已附带【课程资料】原文，请严格遵守：")
                .append("1) 优先依据课程资料回答，术语与记号尽量与资料保持一致；")
                .append("2) 若课程资料中确实没有相关内容，先明确说明")
                .append("「课程资料中未涵盖这一点」，再补充通用知识并标注为通用知识；")
                .append("3) 严禁编造课程资料中不存在的内容；")
                .append("4) citations 只填写你实际依据的资料标题（必须与【课程资料】中的标题完全一致），")
                .append("若仅依据通用知识作答则返回空数组。");
        } else {
            prompt.append("当前没有可用的课程资料，请基于通用学科知识回答，")
                .append("并在答案开头注明「以下为通用知识，供参考」。");
        }

        prompt.append("如果问题不明确，可以追问澄清。")
            .append("输出 JSON:{answer:string,citations:[string]}。");
        return prompt.toString();
    }

    private String buildUserPrompt(String question, String courseName, String knowledgePoint, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("【问题】\n").append(question).append('\n');

        List<Map<String, Object>> docs = toMapList(context.get("docs"));
        List<String> imageOnlyTitles = toStringList(context.get("imageOnlyTitles"));

        prompt.append("\n【课程资料】\n");
        if (StringUtils.hasText(courseName)) {
            prompt.append("课程：").append(courseName).append('\n');
        }
        if (StringUtils.hasText(knowledgePoint)) {
            prompt.append("当前知识点：").append(knowledgePoint).append('\n');
        }
        if (docs.isEmpty() && imageOnlyTitles.isEmpty()) {
            prompt.append("（该知识点下暂无课程资料）\n");
        }

        for (Map<String, Object> doc : docs) {
            prompt.append("\n### 《").append(doc.get("title")).append("》");
            String kp = safe(String.valueOf(doc.getOrDefault("knowledgePoint", "")));
            if (StringUtils.hasText(kp)) {
                prompt.append("（知识点：").append(kp).append("）");
            }
            prompt.append('\n').append(doc.get("text")).append('\n');
        }

        for (String title : imageOnlyTitles) {
            prompt.append("\n### 《").append(title).append("》\n")
                .append("（该资料为扫描图片，系统无法读取正文。")
                .append("若学生问题与该资料相关，请提示其下载查看原件，不要猜测其内容。）\n");
        }

        return prompt.toString();
    }

    private static List<Map<String, Object>> toMapList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object node : list) {
            if (node instanceof Map<?, ?> map) {
                Map<String, Object> item = new LinkedHashMap<>();
                map.forEach((key, value) -> item.put(String.valueOf(key), value));
                out.add(item);
            }
        }
        return out;
    }

    private static List<String> toStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object node : list) {
            if (node == null) {
                continue;
            }
            String text = String.valueOf(node).trim();
            if (!text.isEmpty()) {
                out.add(text);
            }
        }
        return out;
    }

    private static List<String> documentTitles(Map<String, Object> context) {
        List<String> titles = new ArrayList<>();
        for (Map<String, Object> doc : toMapList(context.get("docs"))) {
            String title = String.valueOf(doc.getOrDefault("title", "")).trim();
            if (!title.isEmpty()) {
                titles.add(title);
            }
        }
        return titles;
    }

    private static List<Map<String, Object>> toUsedMaterials(Map<String, Object> context) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> doc : toMapList(context.get("docs"))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("materialId", doc.get("materialId"));
            item.put("title", doc.get("title"));
            out.add(item);
        }
        return out;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
