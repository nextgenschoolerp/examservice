package com.kvs.exam.service;

import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.exam.dto.AddExamSubjectsRequest;
import com.kvs.exam.dto.CreateExamTermRequest;
import com.kvs.exam.dto.CreateGradingScaleRequest;
import com.kvs.exam.dto.ExamSubjectResponse;
import com.kvs.exam.dto.ExamTermResponse;
import com.kvs.exam.dto.GradingScaleResponse;
import com.kvs.exam.entity.AcademicSession;
import com.kvs.exam.entity.ExamSubject;
import com.kvs.exam.entity.ExamTerm;
import com.kvs.exam.entity.Grade;
import com.kvs.exam.entity.GradeBand;
import com.kvs.exam.entity.GradingScale;
import com.kvs.exam.entity.MarkEntry;
import com.kvs.exam.repo.AcademicSessionRepository;
import com.kvs.exam.repo.ExamSubjectRepository;
import com.kvs.exam.repo.ExamTermRepository;
import com.kvs.exam.repo.GradeRepository;
import com.kvs.exam.repo.GradingScaleRepository;
import com.kvs.exam.repo.MarkEntryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Assessment setup.
 * Covers user stories:
 * - US-05000: Configure assessment terms, subject weightage ratios, and grading scales (Exam Controller)
 * - US-05005: Lock examination score entry screens automatically upon timer expiration (T_lock)
 */
@Service
@Slf4j
@Transactional
public class ExamSetupService {

    @Autowired private GradingScaleRepository scaleRepository;
    @Autowired private ExamTermRepository termRepository;
    @Autowired private ExamSubjectRepository subjectRepository;
    @Autowired private MarkEntryRepository markRepository;
    @Autowired private AcademicSessionRepository sessionRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private ExamAccess access;
    @Autowired private StudentAuditService auditService;

    // ------------------------------------------------------------------ grading scales

    public GradingScaleResponse createScale(CreateGradingScaleRequest request) {
        String tenantId = access.tenantFor(request.getTenantId());
        if (scaleRepository.existsByTenantIdAndScaleNameIgnoreCase(tenantId, request.getScaleName().trim())) {
            throw new ConflictException("A grading scale named " + request.getScaleName() + " already exists");
        }
        Set<String> labels = new HashSet<>();
        Set<BigDecimal> mins = new HashSet<>();
        for (CreateGradingScaleRequest.Band b : request.getBands()) {
            if (!labels.add(b.getGradeLabel().trim().toUpperCase()) || !mins.add(b.getMinPercent().stripTrailingZeros())) {
                throw new BadRequestException("Each band needs its own grade label and minimum percentage");
            }
        }
        if (request.getBands().stream().noneMatch(b -> b.getMinPercent().signum() == 0)) {
            throw new BadRequestException("One band must start at 0% so every percentage gets a grade");
        }
        GradingScale scale = GradingScale.builder()
                .tenantId(tenantId).scaleName(request.getScaleName().trim()).passPercent(request.getPassPercent())
                .bands(new ArrayList<>(request.getBands().stream()
                        .sorted(Comparator.comparing(CreateGradingScaleRequest.Band::getMinPercent).reversed())
                        .map(b -> GradeBand.builder().gradeLabel(b.getGradeLabel().trim()).minPercent(b.getMinPercent())
                                .gradePoint(b.getGradePoint()).description(b.getDescription()).build())
                        .toList()))
                .build();
        scale = scaleRepository.save(scale);
        auditService.logSuccess("grading-scale:" + scale.getGradingScaleId(), "GRADING_SCALE_CREATED",
                scale.getScaleName() + " with " + scale.getBands().size() + " bands, pass " + scale.getPassPercent() + "%");
        return toResponse(scale);
    }

    @Transactional(readOnly = true)
    public List<GradingScaleResponse> listScales(String tenantId) {
        return scaleRepository.findByTenantIdOrderByScaleNameAsc(access.tenantFor(tenantId)).stream().map(this::toResponse).toList();
    }

    public GradingScale scale(UUID gradingScaleId, String tenantId) {
        GradingScale scale = scaleRepository.findById(gradingScaleId)
                .orElseThrow(() -> new ResourceNotFoundException("GradingScale", "gradingScaleId", gradingScaleId));
        if (!scale.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("GradingScale", "gradingScaleId", gradingScaleId);
        }
        return scale;
    }

    private GradingScaleResponse toResponse(GradingScale s) {
        return GradingScaleResponse.builder()
                .gradingScaleId(s.getGradingScaleId()).scaleName(s.getScaleName()).passPercent(s.getPassPercent())
                .bands(s.getBands().stream().map(b -> GradingScaleResponse.Band.builder().gradeLabel(b.getGradeLabel())
                        .minPercent(b.getMinPercent()).gradePoint(b.getGradePoint()).description(b.getDescription()).build()).toList())
                .build();
    }

    // ------------------------------------------------------------------ terms

    public ExamTermResponse createTerm(CreateExamTermRequest request) {
        String tenantId = access.tenantFor(request.getTenantId());
        AcademicSession session = sessionRepository.findById(request.getAcademicYearId())
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("AcademicSession", "academicYearId", request.getAcademicYearId()));
        scale(request.getGradingScaleId(), tenantId);
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("endDate must not be before startDate");
        }
        if (request.getStartDate().isBefore(session.getStartDate()) || request.getEndDate().isAfter(session.getEndDate())) {
            throw new BadRequestException("The term must fall within the academic year " + session.getSessionName()
                    + " (" + session.getStartDate() + " to " + session.getEndDate() + ")");
        }
        if (request.getMarkEntryDeadline().isBefore(request.getStartDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant())) {
            throw new BadRequestException("markEntryDeadline must not be before the term starts");
        }
        String name = request.getTermName().trim();
        if (termRepository.existsByTenantIdAndAcademicYearIdAndTermNameIgnoreCase(tenantId, session.getAcademicYearId(), name)) {
            throw new ConflictException("A term named " + name + " already exists in " + session.getSessionName());
        }
        int used = termRepository.findByTenantIdAndAcademicYearIdOrderByStartDateAsc(tenantId, session.getAcademicYearId())
                .stream().mapToInt(ExamTerm::getWeightagePercent).sum();
        if (used + request.getWeightagePercent() > 100) {
            throw new BadRequestException("Term weightages of " + session.getSessionName() + " would exceed 100% (" + used
                    + "% already used)");
        }
        ExamTerm term = termRepository.save(ExamTerm.builder()
                .tenantId(tenantId).academicYearId(session.getAcademicYearId()).termName(name).termType(request.getTermType())
                .weightagePercent(request.getWeightagePercent()).startDate(request.getStartDate()).endDate(request.getEndDate())
                .markEntryDeadline(request.getMarkEntryDeadline()).gradingScaleId(request.getGradingScaleId())
                .build());
        auditService.logSuccess("exam-term:" + term.getExamTermId(), "EXAM_TERM_CREATED",
                name + " (" + term.getTermType() + ", " + term.getWeightagePercent() + "%) in " + session.getSessionName()
                        + ", T_lock " + term.getMarkEntryDeadline());
        return toResponse(term);
    }

    @Transactional(readOnly = true)
    public List<ExamTermResponse> listTerms(String tenantId, UUID academicYearId) {
        String tenant = access.tenantFor(tenantId);
        List<ExamTerm> terms = academicYearId == null ? termRepository.findByTenantIdOrderByStartDateAsc(tenant)
                : termRepository.findByTenantIdAndAcademicYearIdOrderByStartDateAsc(tenant, academicYearId);
        return terms.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ExamTermResponse getTerm(UUID examTermId) {
        return toResponse(access.term(examTermId));
    }

    /** US-05005: closes mark entry now (before the deadline) and flags the entered marks as locked. */
    public ExamTermResponse lockTerm(UUID examTermId) {
        ExamTerm term = access.term(examTermId);
        Instant now = Instant.now();
        if (term.isLocked(now)) {
            throw new ConflictException(term.getTermName() + " is already locked");
        }
        term.setLockedAt(now);
        List<MarkEntry> entries = markRepository.findByExamTermId(examTermId);
        entries.forEach(e -> e.setLocked(true));
        auditService.logSuccess("exam-term:" + examTermId, "EXAM_TERM_LOCKED",
                term.getTermName() + " locked by hand, " + entries.size() + " mark entries frozen");
        return toResponse(term);
    }

    ExamTermResponse toResponse(ExamTerm t) {
        return ExamTermResponse.builder()
                .examTermId(t.getExamTermId()).academicYearId(t.getAcademicYearId())
                .sessionName(sessionRepository.findById(t.getAcademicYearId()).map(AcademicSession::getSessionName).orElse(null))
                .termName(t.getTermName()).termType(t.getTermType()).weightagePercent(t.getWeightagePercent())
                .startDate(t.getStartDate()).endDate(t.getEndDate()).markEntryDeadline(t.getMarkEntryDeadline())
                .locked(t.isLocked(Instant.now())).lockedAt(t.getLockedAt())
                .gradingScaleId(t.getGradingScaleId())
                .gradingScaleName(scaleRepository.findById(t.getGradingScaleId()).map(GradingScale::getScaleName).orElse(null))
                .subjectCount(subjectRepository.findByExamTermIdOrderByDisplayOrderAsc(t.getExamTermId()).size())
                .build();
    }

    // ------------------------------------------------------------------ subjects

    /** Adds the class's subjects to the term; a subject code that already exists is updated. */
    public List<ExamSubjectResponse> addSubjects(UUID examTermId, AddExamSubjectsRequest request) {
        ExamTerm term = access.term(examTermId);
        Grade grade = access.grade(request.getGradeId(), term.getTenantId());
        Set<String> codes = new HashSet<>();
        for (AddExamSubjectsRequest.Subject s : request.getSubjects()) {
            if (!codes.add(s.getSubjectCode())) {
                throw new BadRequestException("Subject " + s.getSubjectCode() + " appears twice");
            }
            if (s.getPassMarks().compareTo(s.getMaxMarks()) > 0) {
                throw new BadRequestException(s.getSubjectCode() + ": passMarks must not exceed maxMarks");
            }
        }
        int order = subjectRepository.findByExamTermIdAndGradeIdOrderByDisplayOrderAsc(examTermId, grade.getGradeId()).size();
        for (AddExamSubjectsRequest.Subject s : request.getSubjects()) {
            ExamSubject subject = subjectRepository.findByExamTermIdAndGradeIdAndSubjectCode(examTermId, grade.getGradeId(), s.getSubjectCode())
                    .orElseGet(() -> ExamSubject.builder().examTermId(examTermId).gradeId(grade.getGradeId()).subjectCode(s.getSubjectCode()).build());
            if (subject.getExamSubjectId() == null) {
                subject.setDisplayOrder(++order);
            }
            subject.setSubjectName(s.getSubjectName().trim());
            subject.setMaxMarks(s.getMaxMarks());
            subject.setPassMarks(s.getPassMarks());
            subject.setWeightage(s.getWeightage() == null ? BigDecimal.ONE : s.getWeightage());
            subjectRepository.save(subject);
        }
        auditService.logSuccess("exam-term:" + examTermId, "EXAM_SUBJECTS_CONFIGURED",
                grade.getGradeName() + ": " + String.join(", ", codes));
        return listSubjects(examTermId, grade.getGradeId());
    }

    @Transactional(readOnly = true)
    public List<ExamSubjectResponse> listSubjects(UUID examTermId, UUID gradeId) {
        ExamTerm term = access.term(examTermId);
        List<ExamSubject> subjects = gradeId == null ? subjectRepository.findByExamTermIdOrderByDisplayOrderAsc(examTermId)
                : subjectRepository.findByExamTermIdAndGradeIdOrderByDisplayOrderAsc(examTermId, access.grade(gradeId, term.getTenantId()).getGradeId());
        return subjects.stream().map(s -> ExamSubjectResponse.builder()
                .examSubjectId(s.getExamSubjectId()).examTermId(examTermId).gradeId(s.getGradeId())
                .gradeName(gradeRepository.findById(s.getGradeId()).map(Grade::getGradeName).orElse(null))
                .subjectCode(s.getSubjectCode()).subjectName(s.getSubjectName()).maxMarks(s.getMaxMarks())
                .passMarks(s.getPassMarks()).weightage(s.getWeightage())
                .build()).toList();
    }
}
