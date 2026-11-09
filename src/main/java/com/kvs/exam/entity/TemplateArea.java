package com.kvs.exam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A co-scholastic area graded on the progress card under an assessment template (read by the progress card in a later step). */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateArea {

    @Column(name = "area_key", nullable = false, length = 50)
    private String areaKey;

    @Column(name = "area_label", nullable = false, length = 100)
    private String areaLabel;
}
