package com.kvs.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** US-05018: the class teacher's narrative remarks on a student's progress card. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressRemarksRequest {

    @NotNull(message = "examTermId is required")
    private UUID examTermId;

    @NotBlank(message = "remarks is required")
    @Size(max = 1000, message = "remarks must be at most 1000 characters")
    private String remarks;
}
