package com.kvs.exam.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** US-05010: publish the term's progress cards of a section to parents. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishResultsRequest {

    @NotNull(message = "sectionId is required")
    private UUID sectionId;
}
