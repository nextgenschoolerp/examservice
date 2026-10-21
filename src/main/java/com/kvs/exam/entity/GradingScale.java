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
 * A grading scale: percentage bands mapped to grade letters and points (US-05000), e.g. CBSE A1 … E.
 */
@Entity
@Table(name = "grading_scales")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradingScale extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "grading_scale_id")
    private UUID gradingScaleId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "scale_name", nullable = false, length = 100)
    private String scaleName;

    /** Minimum overall percentage to pass */
    @Column(name = "pass_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal passPercent;

    /** Bands, highest first */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "grading_scale_bands", joinColumns = @JoinColumn(name = "grading_scale_id"))
    @OrderBy("minPercent DESC")
    @Builder.Default
    private List<GradeBand> bands = new ArrayList<>();
}
