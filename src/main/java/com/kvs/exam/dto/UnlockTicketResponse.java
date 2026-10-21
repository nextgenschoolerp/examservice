package com.kvs.exam.dto;

import com.kvs.exam.enums.UnlockTicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** An unlock ticket. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnlockTicketResponse {

    private UUID ticketId;
    private UUID examTermId;
    private String termName;
    private UUID sectionId;
    private String className;
    private String subjectCode;
    private String subjectName;
    private String requestedBy;
    private String reason;
    private UnlockTicketStatus status;
    private String decidedBy;
    private String decisionRemarks;
    private Instant decidedAt;
    private Instant unlockedUntil;
    private Instant createdAt;
}
