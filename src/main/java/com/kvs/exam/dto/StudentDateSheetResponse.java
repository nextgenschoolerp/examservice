package com.kvs.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** A student's date sheet for one exam term: the papers of the section they sit it in, dated ones first by date and time. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDateSheetResponse {

    private UUID examTermId;
    private String termName;
    private String className;
    /** True while the student is on hold (for example fee dues): no admit card, not cleared to sit the exam */
    private boolean admitCardWithheld;
    /** Why, as the school recorded it; null when cleared or no reason was given */
    private String withheldReason;
    private List<Paper> papers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Paper {
        private String subjectCode;
        private String subjectName;
        private BigDecimal maxMarks;
        /** null until the exam controller schedules the paper */
        private LocalDate examDate;
        private LocalTime startTime;
        private LocalTime endTime;
    }
}
