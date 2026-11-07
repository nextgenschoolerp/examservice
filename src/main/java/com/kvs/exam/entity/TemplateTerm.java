package com.kvs.exam.entity;

import com.kvs.exam.enums.ExamTermType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One term of an assessment template: its name, type and share of the annual result. */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateTerm {

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "term_name", nullable = false, length = 50)
    private String termName;

    @Enumerated(EnumType.STRING)
    @Column(name = "term_type", nullable = false, length = 20)
    private ExamTermType termType;

    @Column(name = "weightage_percent", nullable = false)
    private Integer weightagePercent;
}
