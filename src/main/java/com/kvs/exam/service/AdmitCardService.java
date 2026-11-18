package com.kvs.exam.service;

import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.pdf.SchoolLetterhead;
import com.kvs.core.school.SchoolProfile;
import com.kvs.core.school.SchoolProfileClient;
import com.kvs.exam.dto.StudentDateSheetResponse;
import com.kvs.exam.entity.AcademicSession;
import com.kvs.exam.entity.ExamSubject;
import com.kvs.exam.entity.ExamTerm;
import com.kvs.exam.entity.Section;
import com.kvs.exam.entity.StudentMaster;
import com.kvs.exam.repo.AcademicSessionRepository;
import com.kvs.exam.repo.ExamSubjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Admit cards (hall tickets) for an exam term: for a whole section (exam staff, and class teachers for their own
 * sections) or for one student (staff, and a parent for their own child). Students on hold (for example fee dues) get
 * no card. The cards list the class's date sheet, so at least one paper must have a date.
 */
@Service
@Transactional(readOnly = true)
public class AdmitCardService {

    /** The cards and how many students of the section were left out because they are on hold */
    public record AdmitCards(byte[] pdf, String filename, int cards, int withheld) { }

    @Autowired private ExamAccess access;
    @Autowired private ExamSubjectRepository subjectRepository;
    @Autowired private AcademicSessionRepository sessionRepository;
    @Autowired private SchoolProfileClient schoolProfiles;
    @Autowired private StudentPhotoClient photos;
    @Autowired private AdmitCardPdfWriter writer;

    @Value("${kvs.school.name:School}") private String schoolName;
    @Value("${kvs.time-zone:Asia/Kolkata}") private String timeZone;

    /** Cards for every student of a section, by roll number */
    public AdmitCards forSection(UUID examTermId, UUID sectionId, String branchId) {
        ExamTerm term = access.term(examTermId);
        Section section = access.section(sectionId, term.getTenantId(), ExamAccess.VIEW_TERMS);
        String campus = access.campus(ExamAccess.VIEW_TERMS, branchId);
        List<ExamSubject> papers = dateSheet(term, section);
        List<StudentMaster> roster = access.roster(section, term, campus);
        if (roster.isEmpty()) {
            throw new BadRequestException(access.className(section) + " has no students");
        }
        List<StudentMaster> eligible = roster.stream().filter(s -> !Boolean.TRUE.equals(s.getIsOnHold())).toList();
        if (eligible.isEmpty()) {
            throw new ConflictException("All " + roster.size() + " students of " + access.className(section) + " are on hold, so no admit cards were made");
        }
        String className = access.className(section);
        byte[] pdf = render(term, section, papers, eligible);
        return new AdmitCards(pdf, "Admit-cards-" + AdmitCardPdfWriter.termCode(term.getTermName()) + "-" + className.replace(' ', '-') + ".pdf",
                eligible.size(), roster.size() - eligible.size());
    }

    /** One student's card, in the section they sit the term in */
    public AdmitCards forStudent(UUID examTermId, UUID studentId) {
        ExamTerm term = access.term(examTermId);
        StudentMaster student = access.student(studentId, ExamAccess.VIEW_STUDENTS);
        if (!student.getTenantId().equals(term.getTenantId())) {
            throw new BadRequestException("The student and the exam term belong to different schools");
        }
        if (Boolean.TRUE.equals(student.getIsOnHold())) {
            String reason = student.getActiveHoldReason();
            throw new ConflictException("The admit card is withheld" + (reason == null || reason.isBlank() ? "" : ": " + reason)
                    + ". Please contact the school office.");
        }
        Section section = access.sectionFor(student, term);
        if (section == null) {
            throw new BadRequestException(name(student) + " is not in a section");
        }
        byte[] pdf = render(term, section, dateSheet(term, section), List.of(student));
        return new AdmitCards(pdf, "Admit-card-" + AdmitCardPdfWriter.termCode(term.getTermName()) + "-" + student.getStudentUid() + ".pdf", 1, 0);
    }

    /**
     * A student's date sheet for a term (staff, and a parent for their own child): the papers of the section they sit
     * it in, dated ones first. Empty papers when the class has no subjects; undated papers have null dates.
     */
    public StudentDateSheetResponse dateSheetFor(UUID examTermId, UUID studentId) {
        ExamTerm term = access.term(examTermId);
        StudentMaster student = access.student(studentId, ExamAccess.VIEW_STUDENTS);
        if (!student.getTenantId().equals(term.getTenantId())) {
            throw new BadRequestException("The student and the exam term belong to different schools");
        }
        Section section = access.sectionFor(student, term);
        List<StudentDateSheetResponse.Paper> papers = section == null ? List.of()
                : sorted(subjectRepository.findByExamTermIdAndGradeIdOrderByDisplayOrderAsc(term.getExamTermId(), section.getGradeId()))
                .stream().map(s -> StudentDateSheetResponse.Paper.builder().subjectCode(s.getSubjectCode()).subjectName(s.getSubjectName())
                        .maxMarks(s.getMaxMarks()).examDate(s.getExamDate()).startTime(s.getStartTime()).endTime(s.getEndTime()).build())
                .toList();
        boolean withheld = Boolean.TRUE.equals(student.getIsOnHold());
        String reason = student.getActiveHoldReason();
        return StudentDateSheetResponse.builder().examTermId(term.getExamTermId()).termName(term.getTermName())
                .className(section == null ? null : access.className(section)).papers(papers)
                .admitCardWithheld(withheld).withheldReason(withheld && reason != null && !reason.isBlank() ? reason : null).build();
    }

    private static List<ExamSubject> sorted(List<ExamSubject> subjects) {
        return subjects.stream().sorted(Comparator
                .comparing(ExamSubject::getExamDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ExamSubject::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ExamSubject::getDisplayOrder)).toList();
    }

    /** The class's papers, dated ones first by date and time, then those not scheduled yet */
    private List<ExamSubject> dateSheet(ExamTerm term, Section section) {
        List<ExamSubject> subjects = subjectRepository.findByExamTermIdAndGradeIdOrderByDisplayOrderAsc(term.getExamTermId(), section.getGradeId());
        if (subjects.isEmpty()) {
            throw new BadRequestException("No subjects are set up for " + access.className(section) + " in " + term.getTermName());
        }
        if (subjects.stream().allMatch(s -> s.getExamDate() == null)) {
            throw new BadRequestException("Set the date sheet of " + access.className(section) + " for " + term.getTermName() + " first");
        }
        return sorted(subjects);
    }

    private byte[] render(ExamTerm term, Section section, List<ExamSubject> subjects, List<StudentMaster> students) {
        SchoolProfile profile = schoolProfiles.getOr(term.getTenantId(), SchoolProfile.named(schoolName));
        String session = sessionRepository.findById(term.getAcademicYearId()).map(AcademicSession::getSessionName).orElse("");
        AdmitCardPdfWriter.Exam exam = new AdmitCardPdfWriter.Exam(term.getTermName(), session, access.className(section),
                term.getStartDate(), term.getEndDate());
        List<AdmitCardPdfWriter.Paper> papers = subjects.stream().map(s -> new AdmitCardPdfWriter.Paper(s.getSubjectCode(),
                s.getSubjectName(), s.getMaxMarks(), s.getExamDate(), s.getStartTime(), s.getEndTime())).toList();
        String prefix = AdmitCardPdfWriter.termCode(term.getTermName());
        List<AdmitCardPdfWriter.Card> cards = students.stream().map(s -> new AdmitCardPdfWriter.Card(prefix + "-" + s.getStudentUid(),
                name(s), s.getStudentUid(), s.getRollNumber(), s.getDateOfBirth(),
                s.getProfilePhotoUrl() == null ? null : photos.photo(s.getStudentId()))).toList();
        return writer.write(SchoolLetterhead.from(profile), exam, papers, cards, Instant.now(), ZoneId.of(timeZone));
    }

    private static String name(StudentMaster s) {
        return (s.getFirstName() + " " + (s.getLastName() == null ? "" : s.getLastName())).trim();
    }
}
