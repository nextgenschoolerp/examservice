package com.kvs.exam.entity;

import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A student's holistic progress card for a term: teacher remarks and publication (module5.sql holistic_progress_cards, US-05009, US-05010, US-05018).
 */
@Entity
@Table(name = "holistic_progress_cards", uniqueConstraints = @UniqueConstraint(name = "uk_hpc_term_student", columnNames = {"exam_term_id", "student_id"}))
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressCard extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "hpc_id")
    private UUID hpcId;

    @Column(name = "exam_term_id", nullable = false)
    private UUID examTermId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "teacher_qualitative_remarks", length = 1000)
    private String teacherRemarks;

    @Column(name = "remarks_by", length = 150)
    private String remarksBy;

    @Column(name = "is_published", nullable = false)
    @Builder.Default
    private Boolean published = false;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by", length = 150)
    private String publishedBy;
}
