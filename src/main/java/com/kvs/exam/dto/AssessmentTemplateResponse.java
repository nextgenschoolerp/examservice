package com.kvs.exam.dto;

import com.kvs.exam.enums.ExamTermType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** An assessment template with its terms, grading bands and co-scholastic areas. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentTemplateResponse {

    private UUID templateId;
    /** Stable code of a master template; null for a school copy */
    private String templateCode;
    private String boardCode;
    private String templateName;
    private String description;
    private BigDecimal passPercent;
    private boolean needsReview;
    private String reportLayout;
    /** True for a master template (read-only); false for the school's own copy */
    private boolean master;
    private UUID sourceTemplateId;
    private int totalWeightage;
    private List<Term> terms;
    private List<Area> areas;
    private List<Band> bands;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Term {
        private String termName;
        private ExamTermType termType;
        private Integer weightagePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Area {
        private String areaKey;
        private String areaLabel;
    }

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
