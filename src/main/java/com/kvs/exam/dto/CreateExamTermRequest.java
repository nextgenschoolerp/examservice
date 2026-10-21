package com.kvs.exam.dto;

import com.kvs.exam.enums.ExamTermType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** US-05000: an assessment term with its weightage and mark entry deadline (T_lock). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExamTermRequest {

    /** Optional; defaults to the caller's institution (tenantId claim). */
    private String tenantId;

    @NotNull(message = "academicYearId is required")
    private UUID academicYearId;

    @NotBlank(message = "termName is required")
    @Size(max = 50, message = "termName must be at most 50 characters")
    private String termName;

    @NotNull(message = "termType is required")
    private ExamTermType termType;

    @NotNull(message = "weightagePercent is required")
    @Min(value = 1, message = "weightagePercent must be between 1 and 100")
    @Max(value = 100, message = "weightagePercent must be between 1 and 100")
    private Integer weightagePercent;

    @NotNull(message = "startDate is required")
    private LocalDate startDate;

    @NotNull(message = "endDate is required")
    private LocalDate endDate;

    /** T_lock: mark entry closes at this instant */
    @NotNull(message = "markEntryDeadline is required")
    private Instant markEntryDeadline;

    @NotNull(message = "gradingScaleId is required")
    private UUID gradingScaleId;
}
