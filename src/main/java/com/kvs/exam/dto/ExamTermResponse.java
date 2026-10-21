package com.kvs.exam.dto;

import com.kvs.exam.enums.ExamTermType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** An assessment term with its lock state. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamTermResponse {

    private UUID examTermId;
    private UUID academicYearId;
    private String sessionName;
    private String termName;
    private ExamTermType termType;
    private Integer weightagePercent;
    private LocalDate startDate;
    private LocalDate endDate;
    private Instant markEntryDeadline;
    /** True once the deadline has passed or the exam controller locked the term */
    private boolean locked;
    private Instant lockedAt;
    private UUID gradingScaleId;
    private String gradingScaleName;
    private int subjectCount;
}
