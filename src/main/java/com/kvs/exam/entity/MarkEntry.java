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
 * A student's marks in one subject of a term (module5.sql marks_entry_register, US-05003).
 */
@Entity
@Table(name = "marks_entry_register", uniqueConstraints = @UniqueConstraint(name = "uk_mark_subject_student", columnNames = {"exam_subject_id", "student_id"}), indexes = @Index(name = "idx_marks_student_exam", columnList = "student_id, exam_term_id"))
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkEntry extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "mark_entry_id")
    private UUID markEntryId;

    @Column(name = "exam_term_id", nullable = false)
    private UUID examTermId;

    @Column(name = "exam_subject_id", nullable = false)
    private UUID examSubjectId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    /** Null when the student was absent */
    @Column(name = "marks_obtained", precision = 5, scale = 2)
    private BigDecimal marksObtained;

    @Column(name = "is_absent", nullable = false)
    @Builder.Default
    private Boolean absent = false;

    /** Set when the term is locked by the exam controller (T_lock) */
    @Column(name = "is_locked", nullable = false)
    @Builder.Default
    private Boolean locked = false;

    @Column(name = "evaluated_by", nullable = false, length = 150)
    private String evaluatedBy;

    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt;
}
