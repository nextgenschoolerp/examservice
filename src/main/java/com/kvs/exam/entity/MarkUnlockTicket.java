package com.kvs.exam.entity;

import com.kvs.exam.enums.UnlockTicketStatus;
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
 * A request to reopen locked mark entry for one subject of one section, decided by the principal (module5.sql grade_lock_tickets, US-05006).
 */
@Entity
@Table(name = "grade_lock_tickets")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkUnlockTicket extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ticket_id")
    private UUID ticketId;

    @Column(name = "exam_term_id", nullable = false)
    private UUID examTermId;

    @Column(name = "exam_subject_id", nullable = false)
    private UUID examSubjectId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "requested_by", nullable = false, length = 150)
    private String requestedBy;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    @Builder.Default
    private UnlockTicketStatus status = UnlockTicketStatus.PENDING;

    @Column(name = "decided_by", length = 150)
    private String decidedBy;

    @Column(name = "decision_remarks", length = 500)
    private String decisionRemarks;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** Mark entry for the subject and section is open again until this time */
    @Column(name = "unlocked_until")
    private Instant unlockedUntil;
}
