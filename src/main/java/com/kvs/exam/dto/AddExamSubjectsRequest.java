package com.kvs.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** US-05000: subjects examined in a term for one class, with marks and weightage. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddExamSubjectsRequest {

    @NotNull(message = "gradeId is required")
    private UUID gradeId;

    @NotEmpty(message = "subjects must contain at least one subject")
    @Size(max = 20, message = "at most 20 subjects per class")
    @Valid
    private List<Subject> subjects;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subject {
        @NotBlank(message = "subjectCode is required")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,19}$", message = "subjectCode must be 2-20 upper-case letters, digits or underscores, e.g. MATH")
        private String subjectCode;

        @NotBlank(message = "subjectName is required")
        @Size(max = 100, message = "subjectName must be at most 100 characters")
        private String subjectName;

        @NotNull(message = "maxMarks is required")
        @DecimalMin(value = "1.00", message = "maxMarks must be between 1 and 999")
        @DecimalMax(value = "999.00", message = "maxMarks must be between 1 and 999")
        private BigDecimal maxMarks;

        @NotNull(message = "passMarks is required")
        @DecimalMin(value = "0.00", message = "passMarks must not be negative")
        private BigDecimal passMarks;

        /** Relative weight in the term percentage, default 1 */
        @DecimalMin(value = "0.10", message = "weightage must be between 0.1 and 10")
        @DecimalMax(value = "10.00", message = "weightage must be between 0.1 and 10")
        private BigDecimal weightage;
    }
}
