package com.kvs.exam.dto;

import com.kvs.exam.enums.ExamTermType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** US-05009: NEP 2020 holistic progress card of a student for a term. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressCardResponse {

    private UUID studentId;
    private String studentUid;
    private String studentName;
    private String className;
    private Integer rollNumber;
    private UUID examTermId;
    private String termName;
    private ExamTermType termType;
    private String academicYear;
    private List<SubjectResult> scholastic;
    private BigDecimal totalObtained;
    private BigDecimal totalMax;
    private BigDecimal percentage;
    private String grade;
    private BigDecimal gradePoint;
    /** PASS, FAIL or INCOMPLETE */
    private String result;
    private Integer sectionRank;
    private int sectionSize;
    private List<CoScholasticResponse.AreaGrade> coScholastic;
    private String teacherRemarks;
    private String remarksBy;
    private boolean published;
    private Instant publishedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubjectResult {
        private String subjectCode;
        private String subjectName;
        private BigDecimal maxMarks;
        private BigDecimal marksObtained;
        private boolean absent;
        private boolean entered;
        private BigDecimal percentage;
        private String grade;
        private boolean passed;
    }
}
