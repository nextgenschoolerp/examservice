package com.kvs.exam.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An assessment template of a board: the terms with their weightage, the grading bands, the pass mark and the co-scholastic
 * areas. Master templates (tenantId null, loaded from master/assessment-templates.json) are read-only; a school copies one
 * to edit it, and applies it to create its grading scale and exam terms for an academic year.
 */
@Entity
@Table(name = "assessment_templates", indexes = @Index(name = "idx_assess_tpl_tenant_board", columnList = "tenant_id, board_code"))
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentTemplate extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "template_id")
    private UUID templateId;

    /** Null for a master template; the school's id for its own copy */
    @Column(name = "tenant_id")
    private String tenantId;

    /** Stable code of a master template (CBSE_STANDARD); null for a school copy */
    @Column(name = "template_code", length = 50, unique = true)
    private String templateCode;

    /** education_boards.board_code the template is for; STATE means every state board */
    @Column(name = "board_code", nullable = false, length = 40)
    private String boardCode;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "pass_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal passPercent;

    /** A starting point the school should check against the board's handbook before using it */
    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    /** Progress-card layout key (used by the report cards) */
    @Column(name = "report_layout", length = 30)
    private String reportLayout;

    /** The master template a school copy was made from */
    @Column(name = "source_template_id")
    private UUID sourceTemplateId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "assessment_template_terms", joinColumns = @JoinColumn(name = "template_id"))
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<TemplateTerm> terms = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "assessment_template_areas", joinColumns = @JoinColumn(name = "template_id"))
    @Builder.Default
    private List<TemplateArea> areas = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "assessment_template_bands", joinColumns = @JoinColumn(name = "template_id"))
    @OrderBy("minPercent DESC")
    @Builder.Default
    private List<GradeBand> bands = new ArrayList<>();

    public boolean isMaster() {
        return tenantId == null;
    }
}
