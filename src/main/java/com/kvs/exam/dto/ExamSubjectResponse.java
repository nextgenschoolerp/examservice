package com.kvs.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** A subject of a term for one class. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSubjectResponse {

    private UUID examSubjectId;
    private UUID examTermId;
    private UUID gradeId;
    private String gradeName;
    private String subjectCode;
    private String subjectName;
    private BigDecimal maxMarks;
    private BigDecimal passMarks;
    private BigDecimal weightage;
}
