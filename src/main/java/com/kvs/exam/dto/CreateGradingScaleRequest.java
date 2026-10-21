package com.kvs.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** US-05000: a grading scale with its percentage bands. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGradingScaleRequest {

    /** Optional; defaults to the caller's institution (tenantId claim). */
    private String tenantId;

    @NotBlank(message = "scaleName is required")
    @Size(max = 100, message = "scaleName must be at most 100 characters")
    private String scaleName;

    @NotNull(message = "passPercent is required")
    @DecimalMin(value = "0.00", message = "passPercent must be between 0 and 100")
    @DecimalMax(value = "100.00", message = "passPercent must be between 0 and 100")
    private BigDecimal passPercent;

    @NotEmpty(message = "bands must contain at least one grade band")
    @Size(max = 15, message = "a scale has at most 15 bands")
    @Valid
    private List<Band> bands;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Band {
        @NotBlank(message = "gradeLabel is required")
        @Size(max = 5, message = "gradeLabel must be at most 5 characters")
        private String gradeLabel;

        @NotNull(message = "minPercent is required")
        @DecimalMin(value = "0.00", message = "minPercent must be between 0 and 100")
        @DecimalMax(value = "100.00", message = "minPercent must be between 0 and 100")
        private BigDecimal minPercent;

        @DecimalMin(value = "0.0", message = "gradePoint must not be negative")
        @DecimalMax(value = "10.0", message = "gradePoint must be at most 10")
        private BigDecimal gradePoint;

        @Size(max = 50, message = "description must be at most 50 characters")
        private String description;
    }
}
