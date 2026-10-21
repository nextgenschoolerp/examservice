package com.kvs.exam.entity;

import com.kvs.exam.enums.CoScholasticArea;
import com.kvs.core.entity.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A student's co-scholastic grade in one area for a term (US-05008).
 */
@Entity
@Table(name = "co_scholastic_grades", uniqueConstraints = @UniqueConstraint(name = "uk_co_scholastic_term_student_area", columnNames = {"exam_term_id", "student_id", "area"}))
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoScholasticGrade extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "co_scholastic_id")
    private UUID coScholasticId;

    @Column(name = "exam_term_id", nullable = false)
    private UUID examTermId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "area", nullable = false, length = 40)
    private CoScholasticArea area;

    /** A+, A, B or C */
    @Column(name = "grade", nullable = false, length = 2)
    private String grade;

    @Column(name = "remarks", length = 300)
    private String remarks;

    @Column(name = "assessed_by", nullable = false, length = 150)
    private String assessedBy;
}
