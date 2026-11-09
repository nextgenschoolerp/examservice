package com.kvs.exam.service;

import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.core.rbac.ScopeGuard;
import com.kvs.core.util.SecurityUtils;
import com.kvs.exam.entity.ExamTerm;
import com.kvs.exam.entity.Grade;
import com.kvs.exam.entity.Section;
import com.kvs.exam.entity.StudentMaster;
import com.kvs.exam.enums.StudentStatus;
import com.kvs.exam.repo.ExamTermRepository;
import com.kvs.exam.repo.GradeRepository;
import com.kvs.exam.repo.MarkEntryRepository;
import com.kvs.exam.repo.SectionRepository;
import com.kvs.exam.repo.StudentMasterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * School, scope and roster helpers for the Module 05 assessment services (RBAC Phase 6: data scopes from ms-core's
 * {@link ScopeGuard}). Records of another institution look as if they did not exist. Within the school, the scope the
 * caller holds the permission with decides what they reach:
 * <ul>
 *   <li>OWN (parents): only their own children; anyone else's child looks as if it did not exist;</li>
 *   <li>CLASS (teachers): only the sections they teach or are class teacher of;</li>
 *   <li>BRANCH: only their campus; TENANT: every campus.</li>
 * </ul>
 */
@Component
public class ExamAccess {

    /** Reading marks, results and progress (the progress card's permission, held by parents with OWN scope) */
    public static final String VIEW_STUDENTS = "exam.students.view";
    /** Reading a term's grids, broadsheets, rank lists and completeness */
    public static final String VIEW_TERMS = "exam.terms.view";
    /** Entering marks and co-scholastic grades */
    public static final String ENTER_MARKS = "exam.terms.manage";
    /** Class teacher's remarks on the progress card */
    public static final String WRITE_REMARKS = "exam.students.manage";
    /** Publishing a section's results */
    public static final String PUBLISH = "exam.terms-2.manage";
    /** Asking for a locked subject to be reopened */
    public static final String REQUEST_UNLOCK = "exam.terms-3.manage";

    @Autowired private ScopeGuard tenantGuard;
    @Autowired private ExamTermRepository termRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private StudentMasterRepository studentRepository;
    @Autowired private MarkEntryRepository markRepository;

    /** Students still on roll: promoted students keep studying in their new class */
    private static final Set<StudentStatus> ON_ROLL = Set.of(StudentStatus.ACTIVE, StudentStatus.PROMOTED);

    /** The tenant a request works on: the one it names (if the caller may use it) or the caller's own. */
    public String tenantFor(String requestedTenantId) {
        if (requestedTenantId != null && !requestedTenantId.isBlank()) {
            tenantGuard.requireTenant(requestedTenantId);
            return requestedTenantId;
        }
        return tenantGuard.currentTenantId()
                .orElseThrow(() -> new BadRequestException("tenantId is required (the token carries no institution)"));
    }

    public ExamTerm term(UUID examTermId) {
        ExamTerm term = termRepository.findById(examTermId)
                .orElseThrow(() -> new ResourceNotFoundException("ExamTerm", "examTermId", examTermId));
        tenantGuard.requireTenantRecord(term.getTenantId(), "ExamTerm", "examTermId", examTermId);
        return term;
    }

    public Grade grade(UUID gradeId, String tenantId) {
        Grade grade = gradeRepository.findById(gradeId)
                .orElseThrow(() -> new ResourceNotFoundException("Grade", "gradeId", gradeId));
        if (!grade.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Grade", "gradeId", gradeId);
        }
        return grade;
    }

    /** A section of one of the term's institution's classes. */
    public Section section(UUID sectionId, String tenantId) {
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", "sectionId", sectionId));
        grade(section.getGradeId(), tenantId);
        return section;
    }

    /** A section of the school that the caller reaches with this permission (CLASS scope: one of their own sections) */
    public Section section(UUID sectionId, String tenantId, String permission) {
        Section section = section(sectionId, tenantId);
        tenantGuard.requireClass(permission, section.getSectionId());
        return section;
    }

    /** The sections of a list the caller reaches with this permission (CLASS scope keeps their own; wider scopes keep all) */
    public List<Section> reachable(List<Section> sections, String permission) {
        return sections.stream().filter(s -> tenantGuard.reachesClass(permission, s.getSectionId())).toList();
    }

    /** The campus to read: the caller's own unless they hold the permission school-wide */
    public String campus(String permission, String requestedBranchId) {
        return tenantGuard.branchFor(permission, requestedBranchId);
    }

    public String className(Section section) {
        return gradeRepository.findById(section.getGradeId()).map(Grade::getGradeName).orElse("") + "-" + section.getSectionName();
    }

    /**
     * The students of a section for one term (optionally of one campus), by roll number then name. A student whose marks
     * for the term were entered in this section stays on it after a promotion or section change, so past results keep
     * their class; students on roll in the section join unless their marks for the term were entered in another section.
     */
    public List<StudentMaster> roster(Section section, ExamTerm term, String branchId) {
        Map<UUID, UUID> markedIn = new HashMap<>();
        markRepository.findByExamTermId(term.getExamTermId()).forEach(m -> markedIn.putIfAbsent(m.getStudentId(), m.getSectionId()));
        Set<UUID> ids = new LinkedHashSet<>();
        markedIn.forEach((studentId, sectionId) -> {
            if (section.getSectionId().equals(sectionId)) {
                ids.add(studentId);
            }
        });
        studentRepository.findBySectionIdOrderByRollNumberAsc(section.getSectionId()).stream()
                .filter(s -> ON_ROLL.contains(s.getStudentStatus()) && !markedIn.containsKey(s.getStudentId()))
                .forEach(s -> ids.add(s.getStudentId()));
        return studentRepository.findAllById(ids).stream()
                .filter(s -> s.getTenantId().equals(term.getTenantId()))
                .filter(s -> branchId == null || branchId.isBlank() || branchId.equals(s.getBranchId()))
                .sorted(Comparator.comparing((StudentMaster s) -> s.getRollNumber() == null ? Integer.MAX_VALUE : s.getRollNumber())
                        .thenComparing(StudentMaster::getFirstName))
                .toList();
    }

    /** The section a student sat a term in: where their marks were entered, else their current section. */
    public Section sectionFor(StudentMaster student, ExamTerm term) {
        UUID sectionId = markRepository.findByStudentIdOrderByEnteredAtAsc(student.getStudentId()).stream()
                .filter(m -> m.getExamTermId().equals(term.getExamTermId()) && m.getSectionId() != null)
                .map(m -> m.getSectionId()).findFirst().orElse(student.getSectionId());
        return sectionId == null ? null : sectionRepository.findById(sectionId).orElse(null);
    }

    /** A student the caller may see: same institution, and for a parent only their own child. */
    /** A student whose results the caller may read (see {@link #student(UUID, String)}) */
    public StudentMaster student(UUID studentId) {
        return student(studentId, VIEW_STUDENTS);
    }

    /**
     * A student the caller reaches with this permission: of their school; with OWN scope their own child (anyone else's
     * child looks as if it did not exist); with CLASS scope one of their sections; below TENANT scope on their campus.
     */
    public StudentMaster student(UUID studentId, String permission) {
        StudentMaster s = studentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentMaster", "studentId", studentId));
        tenantGuard.requireTenantRecord(s.getTenantId(), "StudentMaster", "studentId", studentId);
        tenantGuard.requireOwnChild(permission, studentId, s.getParentAccountId());
        if (!tenantGuard.ownOnly(permission)) {
            tenantGuard.requireClass(permission, s.getSectionId());
            tenantGuard.requireBranch(permission, s.getBranchId());
        }
        return s;
    }

    /**
     * True when the caller reads results only for their own children (OWN scope: parents). They then see published
     * results only.
     */
    public boolean isParentOnly() {
        return tenantGuard.ownOnly(VIEW_STUDENTS);
    }

    public String currentUser() {
        return SecurityUtils.getCurrentUserEmail().orElse("system");
    }

    public static String fullName(StudentMaster s) {
        return s.getFirstName() + " " + s.getLastName();
    }
}
