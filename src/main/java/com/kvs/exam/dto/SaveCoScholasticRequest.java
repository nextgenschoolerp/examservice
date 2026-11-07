package com.kvs.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** US-05008: co-scholastic grades (A+, A, B, C) for students in a term. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveCoScholasticRequest {

    @NotEmpty(message = "entries must contain at least one grade")
    @Size(max = 400, message = "at most 400 entries per request")
    @Valid
    private List<Entry> entries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        @NotNull(message = "studentId is required")
        private UUID studentId;

        /** An area key of the term's template (or a CBSE default area when the term follows no template) */
        @NotBlank(message = "area is required")
        @Size(max = 40, message = "area must be at most 40 characters")
        private String area;

        @NotBlank(message = "grade is required")
        @Pattern(regexp = "^(A\\+|A|B|C)$", message = "grade must be A+, A, B or C")
        private String grade;

        @Size(max = 300, message = "remarks must be at most 300 characters")
        private String remarks;
    }
}
