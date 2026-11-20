package com.kvs.exam.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/** Changes the dates of an existing exam term: its start, end and T_lock (mark entry close). The name, weightage and scale stay. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateExamTermDatesRequest {

    @NotNull(message = "startDate is required")
    private LocalDate startDate;

    @NotNull(message = "endDate is required")
    private LocalDate endDate;

    @NotNull(message = "markEntryDeadline is required")
    private Instant markEntryDeadline;
}
