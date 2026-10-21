package com.kvs.exam.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A subject examined in a term for one class, with its maximum and pass marks and weightage (US-05000).
 */
@Entity
@Table(name = "exam_term_subjects", uniqueConstraints = @UniqueConstraint(name = "uk_exam_subject_term_grade_code", columnNames = {"exam_term_id", "grade_id", "subject_code"}))
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSubject extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "exam_subject_id")
    private UUID examSubjectId;

    @Column(name = "exam_term_id", nullable = false)
    private UUID examTermId;

    @Column(name = "grade_id", nullable = false)
    private UUID gradeId;

    /** e.g. MATH, ENG */
    @Column(name = "subject_code", nullable = false, length = 20)
    private String subjectCode;

    @Column(name = "subject_name", nullable = false, length = 100)
    private String subjectName;

    @Column(name = "max_marks", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxMarks;

    @Column(name = "pass_marks", nullable = false, precision = 5, scale = 2)
    private BigDecimal passMarks;

    /** Relative weight of the subject in the term percentage (default 1) */
    @Column(name = "weightage", nullable = false, precision = 4, scale = 2)
    private BigDecimal weightage;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
