package com.kvs.exam.service;

import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.exam.dto.MarkCompletenessResponse;
import com.kvs.exam.dto.MarkSheetResponse;
import com.kvs.exam.dto.SaveMarksRequest;
import com.kvs.exam.dto.UnlockDecisionRequest;
import com.kvs.exam.dto.UnlockTicketRequest;
import com.kvs.exam.dto.UnlockTicketResponse;
import com.kvs.exam.entity.ExamSubject;
import com.kvs.exam.entity.ExamTerm;
import com.kvs.exam.entity.Grade;
import com.kvs.exam.entity.MarkEntry;
import com.kvs.exam.entity.MarkUnlockTicket;
import com.kvs.exam.entity.Section;
import com.kvs.exam.entity.StudentMaster;
import com.kvs.exam.enums.UnlockTicketStatus;
import com.kvs.exam.repo.ExamSubjectRepository;
import com.kvs.exam.repo.GradeRepository;
import com.kvs.exam.repo.MarkEntryRepository;
import com.kvs.exam.repo.MarkUnlockTicketRepository;
import com.kvs.exam.repo.SectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mark entry.
 * Covers user stories:
 * - US-05003: Enter student scholastic scores via responsive web mark entry grids (Subject Teacher)
 * - US-05005: Lock examination score entry screens automatically upon timer expiration (T_lock)
 * - US-05006: Submit formal ticket requests to unlock locked mark sheets for correction
 * - US-05016: Flag incomplete grade entries before initiating final report card compilation (Exam Controller)
 */
@Service
@Slf4j
@Transactional
public class MarkEntryService {

    @Autowired private ExamSubjectRepository subjectRepository;
    @Autowired private MarkEntryRepository markRepository;
    @Autowired private MarkUnlockTicketRepository ticketRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private ExamAccess access;
    @Autowired private StudentAuditService auditService;

    ExamSubject subject(ExamTerm term, Section section, String subjectCode) {
        return subjectRepository.findByExamTermIdAndGradeIdAndSubjectCode(term.getExamTermId(), section.getGradeId(), subjectCode)
                .orElseThrow(() -> new ResourceNotFoundException("ExamSubject", "subjectCode", subjectCode));
    }

    /** Approved ticket that keeps the subject open for the section right now, if any. */
    private Optional<MarkUnlockTicket> openTicket(ExamSubject subject, UUID sectionId, Instant now) {
        return ticketRepository.findByExamSubjectIdAndSectionId(subject.getExamSubjectId(), sectionId).stream()
                .filter(t -> t.getStatus() == UnlockTicketStatus.APPROVED && t.getUnlockedUntil() != null && t.getUnlockedUntil().isAfter(now))
                .findFirst();
    }

    // ------------------------------------------------------------------ US-05003 / US-05005

    @Transactional(readOnly = true)
    public MarkSheetResponse markSheet(UUID examTermId, UUID sectionId, String subjectCode, String branchId) {
        ExamTerm term = access.term(examTermId);
        Section section = access.section(sectionId, term.getTenantId());
        ExamSubject subject = subject(term, section, subjectCode);
        List<StudentMaster> roster = access.roster(section, term, branchId);
        Map<UUID, MarkEntry> marks = markRepository.findByExamSubjectIdIn(List.of(subject.getExamSubjectId())).stream()
                .collect(Collectors.toMap(MarkEntry::getStudentId, Function.identity(), (a, b) -> a));
        Instant now = Instant.now();
        Optional<MarkUnlockTicket> ticket = openTicket(subject, sectionId, now);
        boolean locked = term.isLocked(now) && ticket.isEmpty();
        List<MarkSheetResponse.Row> rows = roster.stream().map(s -> {
            MarkEntry m = marks.get(s.getStudentId());
            return MarkSheetResponse.Row.builder()
                    .studentId(s.getStudentId()).studentUid(s.getStudentUid()).studentName(ExamAccess.fullName(s))
                    .rollNumber(s.getRollNumber()).entered(m != null)
                    .marksObtained(m == null ? null : m.getMarksObtained()).absent(m != null && m.getAbsent())
                    .evaluatedBy(m == null ? null : m.getEvaluatedBy()).enteredAt(m == null ? null : m.getEnteredAt())
                    .build();
        }).toList();
        int entered = (int) rows.stream().filter(MarkSheetResponse.Row::isEntered).count();
        return MarkSheetResponse.builder()
                .examTermId(examTermId).termName(term.getTermName()).sectionId(sectionId).className(access.className(section))
                .examSubjectId(subject.getExamSubjectId()).subjectCode(subject.getSubjectCode()).subjectName(subject.getSubjectName())
                .maxMarks(subject.getMaxMarks()).passMarks(subject.getPassMarks()).markEntryDeadline(term.getMarkEntryDeadline())
                .locked(locked)
                .lockReason(ticket.isPresent() ? "Reopened until " + ticket.get().getUnlockedUntil() + " by an approved unlock ticket"
                        : term.getLockedAt() != null ? "Locked by the exam controller"
                        : now.isAfter(term.getMarkEntryDeadline()) ? "Mark entry closed at the deadline (T_lock)" : null)
                .unlockedUntil(ticket.map(MarkUnlockTicket::getUnlockedUntil).orElse(null))
                .enteredCount(entered).missingCount(rows.size() - entered).rows(rows)
                .build();
    }

    /** Saves the grid; refused once the term is locked unless an approved ticket reopened this subject for the section. */
    public MarkSheetResponse saveMarks(UUID examTermId, SaveMarksRequest request, String branchId) {
        ExamTerm term = access.term(examTermId);
        Section section = access.section(request.getSectionId(), term.getTenantId());
        ExamSubject subject = subject(term, section, request.getSubjectCode());
        Instant now = Instant.now();
        Optional<MarkUnlockTicket> ticket = openTicket(subject, section.getSectionId(), now);
        if (term.isLocked(now) && ticket.isEmpty()) {
            throw new ConflictException("Mark entry for " + term.getTermName() + " is locked"
                    + (term.getLockedAt() == null ? " (deadline " + term.getMarkEntryDeadline() + " passed)" : "")
                    + "; raise an unlock ticket to correct marks");
        }
        Set<UUID> rosterIds = access.roster(section, term, null).stream().map(StudentMaster::getStudentId)
                .collect(Collectors.toSet());
        Map<UUID, MarkEntry> existing = markRepository.findByExamSubjectIdIn(List.of(subject.getExamSubjectId())).stream()
                .collect(Collectors.toMap(MarkEntry::getStudentId, Function.identity(), (a, b) -> a));
        Set<UUID> seen = new HashSet<>();
        String user = access.currentUser();
        int changed = 0;
        for (SaveMarksRequest.Entry e : request.getEntries()) {
            if (!seen.add(e.getStudentId())) {
                throw new BadRequestException("Student " + e.getStudentId() + " appears twice");
            }
            if (!rosterIds.contains(e.getStudentId())) {
                throw new BadRequestException("Student " + e.getStudentId() + " is not on the roll of " + access.className(section));
            }
            boolean absent = Boolean.TRUE.equals(e.getAbsent());
            if (absent == (e.getMarksObtained() != null)) {
                throw new BadRequestException("Enter marks or mark the student absent, not both or neither (student " + e.getStudentId() + ")");
            }
            if (!absent && e.getMarksObtained().compareTo(subject.getMaxMarks()) > 0) {
                throw new BadRequestException("Marks " + e.getMarksObtained() + " exceed the maximum " + subject.getMaxMarks()
                        + " for " + subject.getSubjectName());
            }
            MarkEntry m = existing.getOrDefault(e.getStudentId(), MarkEntry.builder()
                    .examTermId(examTermId).examSubjectId(subject.getExamSubjectId()).studentId(e.getStudentId())
                    .sectionId(section.getSectionId()).build());
            m.setMarksObtained(absent ? null : e.getMarksObtained());
            m.setAbsent(absent);
            m.setEvaluatedBy(user);
            m.setEnteredAt(now);
            markRepository.save(m);
            changed++;
        }
        auditService.logSuccess("exam-term:" + examTermId, "MARKS_ENTERED",
                access.className(section) + " " + subject.getSubjectCode() + ": " + changed + " entries"
                        + (ticket.isPresent() ? " under unlock ticket " + ticket.get().getTicketId() : ""));
        return markSheet(examTermId, section.getSectionId(), subject.getSubjectCode(), branchId);
    }

    // ------------------------------------------------------------------ US-05006

    public UnlockTicketResponse requestUnlock(UUID examTermId, UnlockTicketRequest request) {
        ExamTerm term = access.term(examTermId);
        Section section = access.section(request.getSectionId(), term.getTenantId());
        ExamSubject subject = subject(term, section, request.getSubjectCode());
        Instant now = Instant.now();
        if (!term.isLocked(now)) {
            throw new ConflictException(term.getTermName() + " is still open for mark entry; no ticket is needed");
        }
        List<MarkUnlockTicket> tickets = ticketRepository.findByExamSubjectIdAndSectionId(subject.getExamSubjectId(), section.getSectionId());
        if (tickets.stream().anyMatch(t -> t.getStatus() == UnlockTicketStatus.PENDING)) {
            throw new ConflictException("A ticket for " + access.className(section) + " " + subject.getSubjectName() + " is already pending");
        }
        if (openTicket(subject, section.getSectionId(), now).isPresent()) {
            throw new ConflictException(access.className(section) + " " + subject.getSubjectName() + " is already reopened");
        }
        MarkUnlockTicket ticket = ticketRepository.save(MarkUnlockTicket.builder()
                .examTermId(examTermId).examSubjectId(subject.getExamSubjectId()).sectionId(section.getSectionId())
                .requestedBy(access.currentUser()).reason(request.getReason().trim()).build());
        auditService.logSuccess("unlock-ticket:" + ticket.getTicketId(), "MARK_UNLOCK_REQUESTED",
                term.getTermName() + " " + access.className(section) + " " + subject.getSubjectCode() + ": " + ticket.getReason());
        return toResponse(ticket, term);
    }

    @Transactional(readOnly = true)
    public List<UnlockTicketResponse> listTickets(UUID examTermId) {
        ExamTerm term = access.term(examTermId);
        return ticketRepository.findByExamTermIdOrderByCreatedAtDesc(examTermId).stream().map(t -> toResponse(t, term)).toList();
    }

    /** Approve (reopens the subject for the section for unlockHours) or reject; the requester cannot decide their own ticket. */
    public UnlockTicketResponse decide(UUID ticketId, UnlockDecisionRequest request) {
        MarkUnlockTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("UnlockTicket", "ticketId", ticketId));
        ExamTerm term = access.term(ticket.getExamTermId());
        if (request.getDecision() == UnlockTicketStatus.PENDING) {
            throw new BadRequestException("decision must be APPROVED or REJECTED");
        }
        if (ticket.getStatus() != UnlockTicketStatus.PENDING) {
            throw new ConflictException("The ticket was already " + ticket.getStatus().name().toLowerCase());
        }
        String user = access.currentUser();
        if (user.equalsIgnoreCase(ticket.getRequestedBy())) {
            throw new ConflictException("A ticket must be decided by someone other than the requester");
        }
        if (request.getDecision() == UnlockTicketStatus.REJECTED && (request.getRemarks() == null || request.getRemarks().isBlank())) {
            throw new BadRequestException("remarks are required when rejecting a ticket");
        }
        Instant now = Instant.now();
        ticket.setStatus(request.getDecision());
        ticket.setDecidedBy(user);
        ticket.setDecidedAt(now);
        ticket.setDecisionRemarks(request.getRemarks());
        if (request.getDecision() == UnlockTicketStatus.APPROVED) {
            ticket.setUnlockedUntil(now.plus(Duration.ofHours(request.getUnlockHours() == null ? 24 : request.getUnlockHours())));
        }
        auditService.logSuccess("unlock-ticket:" + ticketId, "MARK_UNLOCK_" + request.getDecision(),
                term.getTermName() + (ticket.getUnlockedUntil() != null ? ", open until " + ticket.getUnlockedUntil() : "")
                        + (request.getRemarks() == null ? "" : ": " + request.getRemarks()));
        return toResponse(ticket, term);
    }

    private UnlockTicketResponse toResponse(MarkUnlockTicket t, ExamTerm term) {
        ExamSubject subject = subjectRepository.findById(t.getExamSubjectId()).orElse(null);
        Section section = sectionRepository.findById(t.getSectionId()).orElse(null);
        return UnlockTicketResponse.builder()
                .ticketId(t.getTicketId()).examTermId(term.getExamTermId()).termName(term.getTermName())
                .sectionId(t.getSectionId()).className(section == null ? null : access.className(section))
                .subjectCode(subject == null ? null : subject.getSubjectCode()).subjectName(subject == null ? null : subject.getSubjectName())
                .requestedBy(t.getRequestedBy()).reason(t.getReason()).status(t.getStatus()).decidedBy(t.getDecidedBy())
                .decisionRemarks(t.getDecisionRemarks()).decidedAt(t.getDecidedAt()).unlockedUntil(t.getUnlockedUntil())
                .createdAt(t.getCreatedAt())
                .build();
    }

    // ------------------------------------------------------------------ US-05016

    /** Missing mark entries per section and subject: one section, every section of a class, or the whole term. */
    @Transactional(readOnly = true)
    public MarkCompletenessResponse completeness(UUID examTermId, UUID sectionId, UUID gradeId, String branchId) {
        ExamTerm term = access.term(examTermId);
        List<Section> sections = new ArrayList<>();
        if (sectionId != null) {
            sections.add(access.section(sectionId, term.getTenantId()));
        } else {
            List<UUID> grades = gradeId != null ? List.of(access.grade(gradeId, term.getTenantId()).getGradeId())
                    : subjectRepository.findByExamTermIdOrderByDisplayOrderAsc(examTermId).stream().map(ExamSubject::getGradeId).distinct().toList();
            Map<UUID, Integer> order = gradeRepository.findAllById(grades).stream()
                    .collect(Collectors.toMap(Grade::getGradeId, Grade::getSequenceOrder));
            grades.stream().sorted((a, b) -> order.getOrDefault(a, 0) - order.getOrDefault(b, 0))
                    .forEach(g -> sectionRepository.findByGradeId(g).stream()
                            .sorted((a, b) -> a.getSectionName().compareTo(b.getSectionName())).forEach(sections::add));
        }
        List<MarkCompletenessResponse.Item> items = new ArrayList<>();
        for (Section section : sections) {
            List<StudentMaster> roster = access.roster(section, term, branchId);
            if (roster.isEmpty()) {
                continue;
            }
            List<ExamSubject> subjects = subjectRepository.findByExamTermIdAndGradeIdOrderByDisplayOrderAsc(examTermId, section.getGradeId());
            Map<UUID, Set<UUID>> entered = markRepository.findByExamSubjectIdIn(subjects.stream().map(ExamSubject::getExamSubjectId).toList())
                    .stream().collect(Collectors.groupingBy(MarkEntry::getExamSubjectId, Collectors.mapping(MarkEntry::getStudentId, Collectors.toSet())));
            for (ExamSubject subject : subjects) {
                Set<UUID> done = entered.getOrDefault(subject.getExamSubjectId(), Set.of());
                List<MarkCompletenessResponse.MissingStudent> missing = roster.stream().filter(s -> !done.contains(s.getStudentId()))
                        .map(s -> MarkCompletenessResponse.MissingStudent.builder().studentId(s.getStudentId())
                                .studentUid(s.getStudentUid()).studentName(ExamAccess.fullName(s)).build())
                        .toList();
                items.add(MarkCompletenessResponse.Item.builder()
                        .sectionId(section.getSectionId()).className(access.className(section))
                        .subjectCode(subject.getSubjectCode()).subjectName(subject.getSubjectName())
                        .expected(roster.size()).entered(roster.size() - missing.size()).missing(missing)
                        .build());
            }
        }
        int expected = items.stream().mapToInt(MarkCompletenessResponse.Item::getExpected).sum();
        int done = items.stream().mapToInt(MarkCompletenessResponse.Item::getEntered).sum();
        return MarkCompletenessResponse.builder()
                .examTermId(examTermId).termName(term.getTermName()).complete(expected == done)
                .expectedEntries(expected).enteredEntries(done).missingEntries(expected - done)
                .items(items.stream().filter(i -> !i.getMissing().isEmpty() || sectionId != null).toList())
                .build();
    }
}
