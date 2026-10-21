package com.kvs.exam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One band of a grading scale: percentages from {@code minPercent} up to the next band get {@code gradeLabel}. */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeBand {

    @Column(name = "grade_label", nullable = false, length = 5)
    private String gradeLabel;

    @Column(name = "min_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal minPercent;

    @Column(name = "grade_point", precision = 4, scale = 1)
    private BigDecimal gradePoint;

    @Column(name = "description", length = 50)
    private String description;
}
