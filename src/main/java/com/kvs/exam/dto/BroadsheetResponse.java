package com.kvs.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** US-05007: class broadsheet of a term, students × subjects with totals, grades and ranks. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadsheetResponse {

    private UUID examTermId;
    private String termName;
    private UUID sectionId;
    private String className;
    private BigDecimal classAveragePercent;
    private List<SubjectSummary> subjects;
    private List<Row> rows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubjectSummary {
        private String subjectCode;
        private String subjectName;
        private BigDecimal maxMarks;
        private BigDecimal averageMarks;
        private BigDecimal highestMarks;
        private int passed;
        private int failed;
    }
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private UUID studentId;
        private String studentUid;
        private String studentName;
        private Integer rollNumber;
        private List<Cell> marks;
        private BigDecimal totalObtained;
        private BigDecimal totalMax;
        private BigDecimal percentage;
        private String grade;
        /** PASS, FAIL or INCOMPLETE */
        private String result;
        private Integer rank;
    }
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cell {
        private String subjectCode;
        private BigDecimal marksObtained;
        private boolean absent;
        private boolean entered;
        private String grade;
    }
}
