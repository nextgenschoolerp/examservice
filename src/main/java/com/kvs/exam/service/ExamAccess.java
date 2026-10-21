package com.kvs.exam.service;

import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ResourceNotFoundException;
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
 * Tenant, parent and roster helpers for the Module 05 assessment services. Records of another institution look as if
 * they did not exist; a parent (APPLICANT_PARENT without a staff role) only sees their own children.
 */
@Component
public class ExamAccess {

    private static final Set<String> STAFF_ROLES = Set.of("ROLE_EXAM_CONTROLLER", "ROLE_SUBJECT_TEACHER", "ROLE_CLASS_TEACHER",
            "ROLE_SCHOOL_PRINCIPAL", "ROLE_VICE_PRINCIPAL", "ROLE_ACADEMIC_AUDITOR", "ROLE_REGISTRAR", "ROLE_SUPER_ADMIN");

    @Autowired private TenantAccessGuard tenantGuard;
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
    public StudentMaster student(UUID studentId) {
        StudentMaster s = studentRepository.findByStudentId(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentMaster", "studentId", studentId));
        tenantGuard.requireTenantRecord(s.getTenantId(), "StudentMaster", "studentId", studentId);
        if (isParentOnly() && !tenantGuard.currentUserId().map(id -> id.equals(s.getParentAccountId())).orElse(false)) {
            throw new ResourceNotFoundException("StudentMaster", "studentId", studentId);
        }
        return s;
    }

    /** True for a parent account without a school staff role. */
    public boolean isParentOnly() {
        List<String> roles = SecurityUtils.getCurrentUserRoles();
        return roles.contains("ROLE_APPLICANT_PARENT") && roles.stream().noneMatch(STAFF_ROLES::contains);
    }

    public String currentUser() {
        return SecurityUtils.getCurrentUserEmail().orElse("system");
    }

    public static String fullName(StudentMaster s) {
        return s.getFirstName() + " " + s.getLastName();
    }
}
