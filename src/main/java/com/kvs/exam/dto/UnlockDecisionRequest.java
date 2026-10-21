package com.kvs.exam.dto;

import com.kvs.exam.enums.UnlockTicketStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** US-05006: the principal approves or rejects an unlock ticket. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnlockDecisionRequest {

    @NotNull(message = "decision is required")
    private UnlockTicketStatus decision;

    @Size(max = 500, message = "remarks must be at most 500 characters")
    private String remarks;

    /** How long mark entry stays open after approval (default 24 hours) */
    @Min(value = 1, message = "unlockHours must be between 1 and 72")
    @Max(value = 72, message = "unlockHours must be between 1 and 72")
    private Integer unlockHours;
}
