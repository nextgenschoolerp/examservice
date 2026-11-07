package com.kvs.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** What applying a template created: the grading scale (new or already there) and the exam terms. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyTemplateResponse {
    private UUID gradingScaleId;
    private String scaleName;
    /** False when a grading scale of that name already existed and was reused */
    private boolean scaleCreated;
    private List<ExamTermResponse> terms;
}
