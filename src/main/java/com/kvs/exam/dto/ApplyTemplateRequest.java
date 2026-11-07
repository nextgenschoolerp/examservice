package com.kvs.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Use a template for an academic year: creates the school's grading scale and one exam term per template term. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyTemplateRequest {

    /** Optional; defaults to the caller's institution (tenantId claim). */
    private String tenantId;

    @NotNull(message = "academicYearId is required")
    private UUID academicYearId;

    /** The dates of each template term, by term name */
    @NotEmpty(message = "terms must give the dates of every template term")
    @Valid
    private List<TermDates> terms;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TermDates {
        @NotBlank(message = "termName is required")
        private String termName;
        @NotNull(message = "startDate is required")
        private LocalDate startDate;
        @NotNull(message = "endDate is required")
        private LocalDate endDate;
        /** T_lock: mark entry closes at this instant */
        @NotNull(message = "markEntryDeadline is required")
        private Instant markEntryDeadline;
    }
}
