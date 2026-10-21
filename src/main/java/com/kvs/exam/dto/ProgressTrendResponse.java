package com.kvs.exam.dto;

import com.kvs.exam.enums.ExamTermType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** US-05013: term-on-term academic progress of a student (student profile "Academic progress"). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressTrendResponse {

    private UUID studentId;
    private String studentUid;
    private String studentName;
    private String className;
    /** Weighted by term weightage over the terms with results */
    private BigDecimal weightedPercentage;
    private String weightedGrade;
    /** IMPROVING, DECLINING, STEADY or NOT_ENOUGH_DATA (latest term against the one before) */
    private String trend;
    private List<Term> terms;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Term {
        private UUID examTermId;
        private String termName;
        private ExamTermType termType;
        /** the class the student sat this term in */
        private String className;
        private Integer weightagePercent;
        private LocalDate startDate;
        private BigDecimal percentage;
        private String grade;
        private String result;
        private Integer sectionRank;
        private BigDecimal changeFromPrevious;
        private boolean published;
        private List<SubjectPoint> subjects;
    }
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubjectPoint {
        private String subjectCode;
        private String subjectName;
        private BigDecimal percentage;
        private String grade;
    }
}
