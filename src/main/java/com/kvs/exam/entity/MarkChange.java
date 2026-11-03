package com.kvs.exam.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One change to a student's mark in a subject: who entered or corrected it, from what to what, and whether it was done
 * after the term was locked (under an approved unlock ticket). Written by mark entry; the academic auditor reads it.
 */
@Entity
@Table(name = "mark_change_log", indexes = {
        @Index(name = "idx_mark_change_term", columnList = "exam_term_id, changed_at"),
        @Index(name = "idx_mark_change_student", columnList = "student_id")})
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkChange extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "change_id")
    private UUID changeId;

    @Column(name = "exam_term_id", nullable = false)
    private UUID examTermId;

    @Column(name = "exam_subject_id", nullable = false)
    private UUID examSubjectId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "old_marks", precision = 5, scale = 2)
    private BigDecimal oldMarks;

    @Column(name = "old_absent")
    private Boolean oldAbsent;

    @Column(name = "new_marks", precision = 5, scale = 2)
    private BigDecimal newMarks;

    @Column(name = "new_absent", nullable = false)
    private Boolean newAbsent;

    /** ENTERED (first entry), CORRECTED (changed an existing mark) */
    @Column(name = "change_type", nullable = false, length = 20)
    private String changeType;

    /** True when the term was locked and an approved unlock ticket reopened the subject */
    @Column(name = "after_lock", nullable = false)
    private Boolean afterLock;

    @Column(name = "ticket_id")
    private UUID ticketId;

    @Column(name = "reason", length = 300)
    private String reason;

    @Column(name = "changed_by", nullable = false, length = 150)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
}
