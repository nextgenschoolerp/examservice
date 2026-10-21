package com.kvs.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A grading scale. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradingScaleResponse {

    private UUID gradingScaleId;
    private String scaleName;
    private BigDecimal passPercent;
    private List<Band> bands;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Band {
        private String gradeLabel;
        private BigDecimal minPercent;
        private BigDecimal gradePoint;
        private String description;
    }
}
