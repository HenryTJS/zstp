package com.teacher.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 课程资料的纯文本内容缓存。
 *
 * <p>首次需要某份资料的文本时，从原始文件（PDF 等）提取并落库；后续直接读取，
 * 避免每次问答都重新解析文件。若资料被替换为新文件（sourcePath 变化），会自动重新提取。
 */
@Entity
@Table(
    name = "material_texts",
    uniqueConstraints = {@UniqueConstraint(name = "uk_material_text_material", columnNames = {"material_id"})}
)
public class MaterialText {

    /** 提取成功且拿到文本 */
    public static final String STATUS_OK = "OK";
    /** 文件可解析但内容为纯图片（扫描件），没有文本层 */
    public static final String STATUS_NO_TEXT = "NO_TEXT";
    /** 不支持的文件格式 */
    public static final String STATUS_UNSUPPORTED = "UNSUPPORTED";
    /** 提取过程异常 */
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * material.id。这里存纯外键而不做 @OneToOne 关联：
     * PDF 解析耗时较长，不应被包在持有 lazy 关联的长事务里。
     */
    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content = "";

    @Column(name = "char_count", nullable = false)
    private Integer charCount = 0;

    @Column(name = "extract_status", nullable = false, length = 20)
    private String extractStatus = STATUS_OK;

    /** 提取时的源文件路径快照：与当前资料路径不一致时说明文件已替换，需重新提取。 */
    @Column(name = "source_path", length = 600)
    private String sourcePath = "";

    @Column(name = "extracted_at", nullable = false)
    private LocalDateTime extractedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content == null ? "" : content;
    }

    public Integer getCharCount() {
        return charCount;
    }

    public void setCharCount(Integer charCount) {
        this.charCount = charCount == null ? 0 : charCount;
    }

    public String getExtractStatus() {
        return extractStatus;
    }

    public void setExtractStatus(String extractStatus) {
        this.extractStatus = extractStatus == null ? STATUS_OK : extractStatus;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath == null ? "" : sourcePath;
    }

    public LocalDateTime getExtractedAt() {
        return extractedAt;
    }

    public void setExtractedAt(LocalDateTime extractedAt) {
        this.extractedAt = extractedAt == null ? LocalDateTime.now() : extractedAt;
    }
}
