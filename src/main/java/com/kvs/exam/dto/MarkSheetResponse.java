package com.kvs.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** US-05003 / US-05005: the mark entry grid of one subject and section, with its lock state. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkSheetResponse {

    private UUID examTermId;
    private String termName;
    private UUID sectionId;
    private String className;
    private UUID examSubjectId;
    private String subjectCode;
    private String subjectName;
    private BigDecimal maxMarks;
    private BigDecimal passMarks;
    private Instant markEntryDeadline;
    /** True when marks can no longer be changed */
    private boolean locked;
    /** Why the sheet is locked, or that it is open again through an approved ticket */
    private String lockReason;
    /** Open until this time through an approved unlock ticket */
    private Instant unlockedUntil;
    private int enteredCount;
    private int missingCount;
    private List<Row> rows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private UUID studentId;
        private String studentUid;
        private String studentName;
        private Integer rollNumber;
        private BigDecimal marksObtained;
        private boolean absent;
        private boolean entered;
        private String evaluatedBy;
        private Instant enteredAt;
    }
}
