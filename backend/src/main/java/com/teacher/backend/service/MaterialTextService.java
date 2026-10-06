package com.teacher.backend.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.imageio.ImageIO;

import com.teacher.backend.entity.CourseKnowledgePoint;
import com.teacher.backend.entity.Material;
import com.teacher.backend.entity.MaterialText;
import com.teacher.backend.repository.CourseKnowledgePointRepository;
import com.teacher.backend.repository.MaterialRepository;
import com.teacher.backend.repository.MaterialTextRepository;
import com.teacher.backend.util.KnowledgePointUtils;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 课程资料文本提取与检索（RAG 的检索环节）。
 *
 * <p>职责：把「课程 + 知识点」对应的课件解析成纯文本，作为 AI 回答的依据，
 * 使 AI 从"通用聊天机器人"变成"读过课程资料的助教"。
 *
 * <p>要点：
 * <ul>
 *   <li>提取结果落库缓存（{@link MaterialText}），同一文件只解析一次；</li>
 *   <li>纯图片类资料（如扫描版往年题）会被标记为 NO_TEXT，并单独告知 AI
 *       "存在这份资料但无法读取内容"，避免 AI 编造资料内容；</li>
 *   <li>按知识点做层级继承查找，并对精确匹配知识点的资料优先。</li>
 * </ul>
 */
@Service
public class MaterialTextService {

    private static final Logger log = LoggerFactory.getLogger(MaterialTextService.class);

    /** 单份资料注入 AI 上下文的字符上限。 */
    private static final int MAX_CHARS_PER_MATERIAL = 30000;

    /** 一次问答最多附带多少份资料，避免 prompt 过长导致响应变慢。 */
    private static final int MAX_DOCS = 4;

    /** 扫描版 PDF 最多识别多少页，用于控制视觉调用次数与耗时。 */
    private static final int MAX_VISION_PAGES = 8;

    /** 扫描页渲染 DPI：过低影响识别准确率，过高会使 base64 体积超出接口限制。 */
    private static final int VISION_DPI = 110;

    /** 试卷类资料的关键词：用于挑出"往年题"作为生成练习时的题型与分值参考。 */
    private static final List<String> EXAM_STYLE_KEYWORDS = List.of("往年", "真题", "试卷", "期末", "考试", "原卷");

    /**
     * 视觉识别提示词。
     * 强调"只输出图片中真实存在的文字"，防止模型补全或改写题目（幻觉控制）。
     */
    private static final String VISION_SYSTEM_PROMPT = "你是试卷与课件内容识别助手。"
        + "请识别图片中的文字，尽量保持题号、题干、选项与分值的原有顺序和结构。"
        + "输出 JSON:{text:string}。要求："
        + "1) 只输出图片中真实存在的文字，不要补充、改写或推测题目内容；"
        + "2) 数学符号用纯文本表示（如 x^2、log2(n)、O(n log n)）；"
        + "3) 若部分内容模糊无法辨认，请在 text 中如实说明；"
        + "4) 若图片中没有任何文字，text 返回空字符串。";

    private final MaterialRepository materialRepository;
    private final MaterialTextRepository materialTextRepository;
    private final CourseKnowledgePointRepository courseKnowledgePointRepository;
    private final AiClient aiClient;

    public MaterialTextService(
        MaterialRepository materialRepository,
        MaterialTextRepository materialTextRepository,
        CourseKnowledgePointRepository courseKnowledgePointRepository,
        AiClient aiClient
    ) {
        this.materialRepository = materialRepository;
        this.materialTextRepository = materialTextRepository;
        this.courseKnowledgePointRepository = courseKnowledgePointRepository;
        this.aiClient = aiClient;
    }

    /**
     * 收集某知识点相关的课程资料文本，供 AI 作为回答依据。
     *
     * @return { docs: [{materialId,title,knowledgePoint,charCount,text}],
     *           imageOnlyTitles: [string], totalChars: int, hasText: boolean }
     */
    public Map<String, Object> buildContext(String courseName, String knowledgePoint, int maxTotalChars) {
        List<Map<String, Object>> docs = new ArrayList<>();
        List<String> imageOnlyTitles = new ArrayList<>();
        int totalChars = 0;

        for (Material material : findMaterials(courseName, knowledgePoint)) {
            if (docs.size() >= MAX_DOCS || totalChars >= maxTotalChars) {
                break;
            }

            MaterialText record = getOrExtractText(material);
            if (record == null) {
                continue;
            }

            String content = record.getContent() == null ? "" : record.getContent();
            if (!MaterialText.STATUS_OK.equals(record.getExtractStatus()) || !StringUtils.hasText(content)) {
                // 扫描件/不支持格式：内容读不到，但要如实告诉 AI 存在该资料
                imageOnlyTitles.add(safe(material.getTitle()));
                continue;
            }

            if (content.length() > MAX_CHARS_PER_MATERIAL) {
                content = content.substring(0, MAX_CHARS_PER_MATERIAL);
            }
            int remain = maxTotalChars - totalChars;
            if (content.length() > remain) {
                if (remain < 500) {
                    break;
                }
                content = content.substring(0, remain);
            }

            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("materialId", material.getId());
            doc.put("title", safe(material.getTitle()));
            doc.put("knowledgePoint", safe(material.getKnowledgePoint()));
            doc.put("charCount", content.length());
            doc.put("text", content);
            docs.add(doc);
            totalChars += content.length();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("docs", docs);
        out.put("imageOnlyTitles", imageOnlyTitles);
        out.put("totalChars", totalChars);
        out.put("hasText", !docs.isEmpty());
        return out;
    }

    /**
     * 取"往年题/试卷"类资料的文本，作为生成练习时的题型与分值参考。
     *
     * <p>只挑选标题或文件名像试卷的资料（往年题/真题/期末/原卷等），
     * 并限制长度，避免把整份课件塞进 prompt 拖慢出题速度。
     *
     * @return 形如 "《往年题》\n<正文>" 的参考文本；没有可用试卷类资料时返回空串
     */
    public String buildExamStyleReference(String courseName, String knowledgePoint, int maxChars) {
        for (Material material : findMaterials(courseName, knowledgePoint)) {
            String title = safe(material.getTitle()) + " " + safe(material.getFileName());
            boolean examLike = EXAM_STYLE_KEYWORDS.stream().anyMatch(title::contains);
            if (!examLike) {
                continue;
            }

            MaterialText record = getOrExtractText(material);
            if (record == null || !MaterialText.STATUS_OK.equals(record.getExtractStatus())) {
                continue;
            }

            String content = record.getContent() == null ? "" : record.getContent();
            if (!StringUtils.hasText(content)) {
                continue;
            }
            if (content.length() > maxChars) {
                content = content.substring(0, maxChars);
            }
            return "《" + safe(material.getTitle()) + "》\n" + content;
        }
        return "";
    }

    /**
     * 按知识点（含父级与子级继承）查资料，精确匹配该知识点的资料优先。
     */
    private List<Material> findMaterials(String courseName, String knowledgePoint) {
        if (!StringUtils.hasText(courseName) || !StringUtils.hasText(knowledgePoint)) {
            return List.of();
        }
        try {
            List<CourseKnowledgePoint> allPoints =
                courseKnowledgePointRepository.findByCourseNameOrderBySortOrderAscIdAsc(courseName);

            List<String> queryPoints = new ArrayList<>(KnowledgePointUtils.getAllAncestors(knowledgePoint, allPoints));
            queryPoints.addAll(KnowledgePointUtils.getAllDescendants(knowledgePoint, allPoints));
            List<String> distinctPoints = queryPoints.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
            if (distinctPoints.isEmpty()) {
                return List.of();
            }

            return materialRepository.findByCourseNameAndKnowledgePointIn(courseName, distinctPoints).stream()
                .sorted(Comparator
                    .comparingInt((Material material) -> knowledgePoint.equals(safe(material.getKnowledgePoint())) ? 0 : 1)
                    .thenComparing(Material::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        } catch (Exception exception) {
            log.warn("findMaterials failed: {}", exception.getMessage());
            return List.of();
        }
    }

    /**
     * 取资料文本。首次调用解析文件并落库；已缓存且文件未变则直接复用。
     * 提取失败或无文本也会记录状态，避免每次问答重复解析同一个扫描件。
     */
    public MaterialText getOrExtractText(Material material) {
        if (material == null || material.getId() == null) {
            return null;
        }

        String currentPath = safe(material.getFilePath());
        Optional<MaterialText> existing = materialTextRepository.findByMaterialId(material.getId());
        if (existing.isPresent() && currentPath.equals(safe(existing.get().getSourcePath()))) {
            return existing.get();
        }

        MaterialText record = existing.orElseGet(MaterialText::new);
        record.setMaterialId(material.getId());
        record.setSourcePath(currentPath);

        String extracted = extractFromFile(currentPath, material.getFileName(), material.getContentType());
        if (extracted == null) {
            record.setExtractStatus(MaterialText.STATUS_FAILED);
            record.setContent("");
            record.setCharCount(0);
        } else if (extracted.isBlank()) {
            record.setExtractStatus(MaterialText.STATUS_NO_TEXT);
            record.setContent("");
            record.setCharCount(0);
        } else {
            record.setExtractStatus(MaterialText.STATUS_OK);
            record.setContent(extracted);
            record.setCharCount(extracted.length());
        }
        record.setExtractedAt(LocalDateTime.now());
        return materialTextRepository.save(record);
    }

    /**
     * 从文件提取纯文本。返回 null 表示提取异常；返回空串表示当前无法获取内容。
     *
     * <p>PDF 优先走文本层提取；若为扫描件（无文本层）则回退到视觉模型识别，
     * 使图片版往年题也能进入课程资料问答。
     */
    private String extractFromFile(String filePath, String fileName, String contentType) {
        if (!StringUtils.hasText(filePath)) {
            return null;
        }
        File file = new File(filePath);
        if (!file.isFile()) {
            log.warn("material file not found: {}", filePath);
            return null;
        }

        String lower = (safe(fileName) + " " + safe(contentType)).toLowerCase();
        if (lower.contains("pdf")) {
            String text = extractPdf(file);
            if (text != null && !text.isBlank()) {
                return text;
            }
            // 无文本层（扫描件）或提取异常（字体损坏等）都回退到视觉识别，
            // 使图片版往年题、以及因嵌入字体损坏而解析失败的课件都能进入课程资料问答
            log.info("PDF 文本层不可用（{}），改用视觉模型识别: {}",
                text == null ? "提取异常" : "无文本", fileName);
            return extractPdfViaVision(file);
        }

        if (isImageFile(lower)) {
            log.info("图片资料，使用视觉模型识别: {}", fileName);
            return extractImageViaVision(file);
        }

        log.info("material text extraction skipped (unsupported type): {}", fileName);
        return "";
    }

    private static boolean isImageFile(String lower) {
        return lower.contains("image/")
            || lower.endsWith(".png")
            || lower.endsWith(".jpg")
            || lower.endsWith(".jpeg")
            || lower.endsWith(".webp")
            || lower.endsWith(".bmp");
    }

    /**
     * 扫描版 PDF：逐页渲染为 PNG，交给视觉模型识别后拼接。
     * 单页识别失败不影响其他页，尽量拿到可用内容。
     */
    private String extractPdfViaVision(File file) {
        if (!aiClient.aiEnabled()) {
            log.info("AI 未启用，跳过扫描件识别: {}", file.getName());
            return "";
        }
        try (PDDocument document = Loader.loadPDF(file)) {
            int totalPages = document.getNumberOfPages();
            int pageCount = Math.min(totalPages, MAX_VISION_PAGES);
            if (pageCount <= 0) {
                return "";
            }

            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder out = new StringBuilder();
            for (int index = 0; index < pageCount; index++) {
                try {
                    BufferedImage image = renderer.renderImageWithDPI(index, VISION_DPI);
                    String pageText = recognizeImage(
                        toPngBase64(image), file.getName(), index + 1, totalPages, "image/png");
                    if (StringUtils.hasText(pageText)) {
                        out.append("【第 ").append(index + 1).append(" 页】\n")
                           .append(pageText).append("\n\n");
                    }
                } catch (Exception pageError) {
                    log.warn("识别第 {} 页失败（{}）: {}", index + 1, file.getName(), pageError.getMessage());
                }
            }
            if (totalPages > pageCount) {
                out.insert(0, "（该文件共 " + totalPages + " 页，已识别前 " + pageCount + " 页）\n\n");
            }
            return normalize(out.toString());
        } catch (Exception exception) {
            log.warn("extractPdfViaVision failed for {}: {}", file.getName(), exception.getMessage());
            return "";
        }
    }

    /** 图片资料：直接交给视觉模型识别。 */
    private String extractImageViaVision(File file) {
        if (!aiClient.aiEnabled()) {
            return "";
        }
        try {
            String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath()));
            String text = recognizeImage(base64, file.getName(), 1, 1, guessImageMime(file.getName()));
            return normalize(text);
        } catch (Exception exception) {
            log.warn("extractImageViaVision failed for {}: {}", file.getName(), exception.getMessage());
            return "";
        }
    }

    private String recognizeImage(String base64, String fileName, int pageNumber, int totalPages, String mimeType)
            throws IOException, InterruptedException {
        String userPrompt = "请识别这张图片中的题目与文字内容（文件：" + fileName
            + "，第 " + pageNumber + "/" + totalPages + " 页）。";
        Map<String, Object> parsed = aiClient.chatJsonWithImage(
            VISION_SYSTEM_PROMPT, userPrompt, base64, mimeType);
        Object text = parsed == null ? null : parsed.get("text");
        return text == null ? "" : String.valueOf(text).trim();
    }

    private static String toPngBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "png", buffer);
        return Base64.getEncoder().encodeToString(buffer.toByteArray());
    }

    private static String guessImageMime(String fileName) {
        String lower = safe(fileName).toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "image/png";
    }

    private String extractPdf(File file) {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // 按位置排序，尽量还原阅读顺序（课件常为多栏排版）
            stripper.setSortByPosition(true);
            return normalize(stripper.getText(document));
        } catch (Exception exception) {
            log.warn("extractPdf failed for {}: {}", file.getName(), exception.getMessage());
            return null;
        }
    }

    /** 规范化提取文本：压缩连续空白、收敛多余空行。 */
    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replaceAll("[ \\t\\x0B\\f]+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
