package com.teacher.backend.dto;

public record GradeAnswerRequest(
	String question,
	String referenceAnswer,
	String studentAnswer,
	String questionType,
	String studentAnswerImageBase64,
	String studentAnswerImageName,
	Integer fullScore
) {
}
