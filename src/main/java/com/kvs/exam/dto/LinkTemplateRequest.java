package com.kvs.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Make an exam term follow an assessment template (its co-scholastic areas and progress-card layout); null clears it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkTemplateRequest {
    private UUID templateId;
}
