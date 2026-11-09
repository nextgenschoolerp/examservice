package com.kvs.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** US-05008: co-scholastic grades of students in a term. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoScholasticResponse {

    private UUID examTermId;
    private String termName;
    /** The areas graded for this term: its template's, or the CBSE defaults when the term follows no template */
    private List<AreaGrade> areas;
    private List<Row> rows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private UUID studentId;
        private String studentUid;
        private String studentName;
        private List<AreaGrade> grades;
    }
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AreaGrade {
        /** The area's key */
        private String area;
        /** The area's name on the progress card (from the term's template) */
        private String areaLabel;
        private String grade;
        private String remarks;
    }
}
