package com.kvs.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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

/** US-05003: marks of one subject for the students of a section (web mark entry grid). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveMarksRequest {

    @NotNull(message = "sectionId is required")
    private UUID sectionId;

    @NotBlank(message = "subjectCode is required")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,19}$", message = "subjectCode must be 2-20 upper-case letters, digits or underscores, e.g. MATH")
    private String subjectCode;

    @NotEmpty(message = "entries must contain at least one student")
    @Size(max = 80, message = "at most 80 entries per request")
    @Valid
    private List<Entry> entries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        @NotNull(message = "studentId is required")
        private UUID studentId;

        /** Leave empty when the student was absent */
        @DecimalMin(value = "0.00", message = "marksObtained must not be negative")
        @Digits(integer = 3, fraction = 2, message = "marksObtained must have at most 3 digits and 2 decimals")
        private BigDecimal marksObtained;

        private Boolean absent;
    }
}
