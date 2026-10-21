package com.kvs.exam.entity;

import com.kvs.exam.enums.ExamTermType;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An assessment term of an academic year (module5.sql exam_terms) with its weightage and mark entry deadline T_lock (US-05000, US-05005).
 */
@Entity
@Table(name = "exam_terms", indexes = @Index(name = "idx_exam_term_tenant_year", columnList = "tenant_id, academic_year_id"))
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamTerm extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "exam_term_id")
    private UUID examTermId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    /** e.g. Periodic Test 1, Half-Yearly */
    @Column(name = "term_name", nullable = false, length = 50)
    private String termName;

    @Enumerated(EnumType.STRING)
    @Column(name = "term_type", nullable = false, length = 20)
    private ExamTermType termType;

    /** Share of the term in the annual result, in percent */
    @Column(name = "weightage_percent", nullable = false)
    private Integer weightagePercent;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** T_lock: mark entry closes automatically at this time */
    @Column(name = "mark_entry_deadline", nullable = false)
    private Instant markEntryDeadline;

    /** Set when the exam controller locks mark entry before the deadline */
    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "grading_scale_id", nullable = false)
    private UUID gradingScaleId;

    /** Locked when locked by hand or once the deadline has passed. */
    public boolean isLocked(Instant now) {
        return lockedAt != null || now.isAfter(markEntryDeadline);
    }
}
