package com.kvs.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** US-05016: grade entries still missing before report cards are compiled. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkCompletenessResponse {

    private UUID examTermId;
    private String termName;
    private boolean complete;
    private int expectedEntries;
    private int enteredEntries;
    private int missingEntries;
    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private UUID sectionId;
        private String className;
        private String subjectCode;
        private String subjectName;
        private int expected;
        private int entered;
        private List<MissingStudent> missing;
    }
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissingStudent {
        private UUID studentId;
        private String studentUid;
        private String studentName;
    }
}
