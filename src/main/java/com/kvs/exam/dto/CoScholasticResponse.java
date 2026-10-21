package com.kvs.exam.dto;

import com.kvs.exam.enums.CoScholasticArea;
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
        private CoScholasticArea area;
        private String grade;
        private String remarks;
    }
}
