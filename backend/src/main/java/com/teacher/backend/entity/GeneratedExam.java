package com.teacher.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "generated_exams")
public class GeneratedExam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private Integer durationMinutes;

    @Lob
    @Column(columnDefinition = "text")
    private String questionsJson;

    @Lob
    private byte[] pdfOriginal;

    @Lob
    private byte[] pdfAnswer;

    @Lob
    @Column(columnDefinition = "text")
    private String mdOriginal;

    @Lob
    @Column(columnDefinition = "text")
    private String mdAnswer;

    private Instant createdAt = Instant.now();

    public GeneratedExam() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getQuestionsJson() { return questionsJson; }
    public void setQuestionsJson(String questionsJson) { this.questionsJson = questionsJson; }
    public byte[] getPdfOriginal() { return pdfOriginal; }
    public void setPdfOriginal(byte[] pdfOriginal) { this.pdfOriginal = pdfOriginal; }
    public byte[] getPdfAnswer() { return pdfAnswer; }
    public void setPdfAnswer(byte[] pdfAnswer) { this.pdfAnswer = pdfAnswer; }
    public String getMdOriginal() { return mdOriginal; }
    public void setMdOriginal(String mdOriginal) { this.mdOriginal = mdOriginal; }
    public String getMdAnswer() { return mdAnswer; }
    public void setMdAnswer(String mdAnswer) { this.mdAnswer = mdAnswer; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
