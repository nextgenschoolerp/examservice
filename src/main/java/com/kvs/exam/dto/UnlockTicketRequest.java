package com.kvs.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** US-05006: ask to reopen locked mark entry for one subject of a section. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnlockTicketRequest {

    @NotNull(message = "sectionId is required")
    private UUID sectionId;

    @NotBlank(message = "subjectCode is required")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,19}$", message = "subjectCode must be 2-20 upper-case letters, digits or underscores, e.g. MATH")
    private String subjectCode;

    @NotBlank(message = "reason is required")
    @Size(max = 500, message = "reason must be at most 500 characters")
    private String reason;
}
