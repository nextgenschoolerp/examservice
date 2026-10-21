package com.kvs.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** US-05011: class or section rank list with percentile bands and subject toppers. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankListResponse {

    private UUID examTermId;
    private String termName;
    private String scope;
    private int rankedStudents;
    private List<Entry> ranks;
    private List<Topper> subjectToppers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        private Integer rank;
        private UUID studentId;
        private String studentUid;
        private String studentName;
        private String className;
        private BigDecimal percentage;
        private String grade;
        /** e.g. Top 10%, Top 25%, Top 50%, Below median */
        private String percentileBand;
    }
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Topper {
        private String subjectCode;
        private String subjectName;
        private BigDecimal topMarks;
        private BigDecimal maxMarks;
        private List<String> students;
    }
}
