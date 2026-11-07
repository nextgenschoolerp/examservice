package com.kvs.exam.dto;

import com.kvs.exam.enums.ExamTermType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** Edit a school's own copy of an assessment template. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveAssessmentTemplateRequest {

    @NotBlank(message = "templateName is required")
    @Size(max = 100, message = "templateName must be at most 100 characters")
    private String templateName;

    @Size(max = 500, message = "description must be at most 500 characters")
    private String description;

    @NotNull(message = "passPercent is required")
    @DecimalMin(value = "0.00", message = "passPercent must be between 0 and 100")
    @DecimalMax(value = "100.00", message = "passPercent must be between 0 and 100")
    private BigDecimal passPercent;

    @NotEmpty(message = "a template needs at least one term")
    @Size(max = 10, message = "a template has at most 10 terms")
    @Valid
    private List<Term> terms;

    @Size(max = 12, message = "a template has at most 12 co-scholastic areas")
    @Valid
    private List<Area> areas;

    @NotEmpty(message = "bands must contain at least one grade band")
    @Size(max = 15, message = "a scale has at most 15 bands")
    @Valid
    private List<Band> bands;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Term {
        @NotBlank(message = "termName is required")
        @Size(max = 50, message = "termName must be at most 50 characters")
        private String termName;
        @NotNull(message = "termType is required")
        private ExamTermType termType;
        @NotNull(message = "weightagePercent is required")
        @Min(value = 1, message = "weightagePercent must be between 1 and 100")
        @Max(value = 100, message = "weightagePercent must be between 1 and 100")
        private Integer weightagePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Area {
        @NotBlank(message = "areaKey is required")
        @Size(max = 40, message = "areaKey must be at most 40 characters")
        private String areaKey;
        @NotBlank(message = "areaLabel is required")
        @Size(max = 100, message = "areaLabel must be at most 100 characters")
        private String areaLabel;
    }

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
