package com.kvs.exam.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One line of the marks change history (moderation trail). */
@Data
@Builder
public class MarkChangeResponse {
    private UUID changeId;
    private UUID examTermId;
    private String termName;
    private String className;
    private String subjectCode;
    private String subjectName;
    private UUID studentId;
    private String studentUid;
    private String studentName;
    private BigDecimal oldMarks;
    private Boolean oldAbsent;
    private BigDecimal newMarks;
    private Boolean newAbsent;
    private String changeType;
    private boolean afterLock;
    private UUID ticketId;
    private String reason;
    private String changedBy;
    private Instant changedAt;
}
