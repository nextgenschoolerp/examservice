package com.kvs.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** The date sheet of one class in a term: the date and time of each paper (printed on admit cards). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveDateSheetRequest {

    @NotNull(message = "gradeId is required")
    private UUID gradeId;

    @NotEmpty(message = "entries must contain at least one subject")
    @Size(max = 20, message = "at most 20 subjects per class")
    @Valid
    private List<Entry> entries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        @NotBlank(message = "subjectCode is required")
        private String subjectCode;

        /** null clears the subject's date and times */
        private LocalDate examDate;

        /** Start and end come together; end after start */
        private LocalTime startTime;

        private LocalTime endTime;
    }
}
