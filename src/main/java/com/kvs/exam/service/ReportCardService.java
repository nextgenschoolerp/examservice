package com.kvs.exam.service;

import com.kvs.verify.Verification;
import com.kvs.core.exception.BadRequestException;
import com.kvs.core.pdf.SchoolLetterhead;
import com.kvs.verify.DocumentRegistry;
import com.kvs.core.school.SchoolProfile;
import com.kvs.core.school.SchoolProfileClient;
import com.kvs.exam.dto.ProgressCardResponse;
import com.kvs.exam.entity.ExamTerm;
import com.kvs.exam.entity.Section;
import com.kvs.exam.entity.StudentMaster;
import com.kvs.exam.repo.ExamSubjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Report card PDFs: the progress card ({@link ExamResultService#progressCard}) printed on the school letterhead, for
 * one student (staff, and a parent for their own child once published) or a whole section (exam staff, and class
 * teachers for their own sections). Unpublished cards carry a DRAFT watermark.
 */
@Service
@Transactional(readOnly = true)
public class ReportCardService {

    /** The PDF, its file name and how many cards it holds */
    public record ReportCards(byte[] pdf, String filename, int cards) { }

    @Autowired private ExamAccess access;
    @Autowired private ExamResultService results;
    @Autowired private ExamSubjectRepository subjectRepository;
    @Autowired private SchoolProfileClient schoolProfiles;
    @Autowired private StudentPhotoClient photos;
    @Autowired private ReportCardPdfWriter writer;
    @Autowired private ClassTeacherSignatureClient classTeachers;
    @Autowired(required = false) private DocumentRegistry documentRegistry;

    @Value("${kvs.school.name:School}") private String schoolName;
    @Value("${kvs.time-zone:Asia/Kolkata}") private String timeZone;

    /** One student's report card (the same access as the progress card on screen) */
    public ReportCards forStudent(UUID examTermId, UUID studentId) {
        ProgressCardResponse card = results.progressCard(studentId, examTermId);
        StudentMaster student = access.student(studentId);
        com.kvs.core.school.SchoolProfile.Signatory teacher = classTeacher(examTermId, student);
        byte[] pdf = render(student.getTenantId(), List.of(new ReportCardPdfWriter.Card(card, photo(student), null, teacher)));
        return new ReportCards(pdf, "Report-card-" + AdmitCardPdfWriter.termCode(card.getTermName()) + "-" + card.getStudentUid() + ".pdf", 1);
    }

    /** Every student of a section, by roll number, each starting on a new page */
    public ReportCards forSection(UUID examTermId, UUID sectionId, String branchId) {
        ExamTerm term = access.term(examTermId);
        Section section = access.section(sectionId, term.getTenantId(), ExamAccess.VIEW_TERMS);
        String campus = access.campus(ExamAccess.VIEW_TERMS, branchId);
        if (subjectRepository.findByExamTermIdAndGradeIdOrderByDisplayOrderAsc(term.getExamTermId(), section.getGradeId()).isEmpty()) {
            throw new BadRequestException("No subjects are set up for " + access.className(section) + " in " + term.getTermName());
        }
        List<StudentMaster> roster = access.roster(section, term, campus);
        if (roster.isEmpty()) {
            throw new BadRequestException(access.className(section) + " has no students");
        }
        com.kvs.core.school.SchoolProfile.Signatory teacher = classTeachers.forSection(section, term.getTenantId());
        List<ReportCardPdfWriter.Card> cards = roster.stream()
                .map(s -> new ReportCardPdfWriter.Card(results.progressCard(s.getStudentId(), examTermId), photo(s), null, teacher)).toList();
        byte[] pdf = render(term.getTenantId(), cards);
        return new ReportCards(pdf, "Report-cards-" + AdmitCardPdfWriter.termCode(term.getTermName()) + "-"
                + access.className(section).replace(' ', '-') + ".pdf", cards.size());
    }

    /** The class teacher of the student's section in that term, or null when it cannot be worked out */
    private com.kvs.core.school.SchoolProfile.Signatory classTeacher(UUID examTermId, StudentMaster student) {
        try {
            ExamTerm term = access.term(examTermId);
            return classTeachers.forSection(access.sectionFor(student, term), term.getTenantId());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private byte[] photo(StudentMaster s) {
        return s.getProfilePhotoUrl() == null ? null : photos.photo(s.getStudentId());
    }

    private byte[] render(String tenantId, List<ReportCardPdfWriter.Card> cards) {
        SchoolProfile profile = schoolProfiles.getOr(tenantId, SchoolProfile.named(schoolName));
        return writer.write(SchoolLetterhead.from(profile), verified(tenantId, cards), Instant.now(), ZoneId.of(timeZone));
    }

    /**
     * Verification codes for the published cards (a DRAFT gets none): one code per student and term, the same on every
     * download; when results are corrected and republished the facts differ, so the card gets a new code and the old
     * one reads "replaced by a newer version".
     */
    private List<ReportCardPdfWriter.Card> verified(String tenantId, List<ReportCardPdfWriter.Card> cards) {
        if (documentRegistry == null) return cards;
        List<ReportCardPdfWriter.Card> published = cards.stream().filter(c -> c.data().isPublished()).toList();
        if (published.isEmpty()) return cards;
        List<String> codes = documentRegistry.codes(published.stream().map(c -> {
            ProgressCardResponse d = c.data();
            return new DocumentRegistry.Issue(tenantId, "REPORT_CARD", d.getAcademicYear() + "/" + d.getTermName() + "/" + d.getStudentUid(),
                    "Report card, " + d.getTermName(), d.getStudentName(), List.of(
                    new DocumentRegistry.Fact("Examination", d.getTermName() + ", " + d.getAcademicYear()),
                    new DocumentRegistry.Fact("Class", d.getClassName()),
                    new DocumentRegistry.Fact("Marks", ReportCardPdfWriter.num(d.getTotalObtained()) + " of " + ReportCardPdfWriter.num(d.getTotalMax())),
                    new DocumentRegistry.Fact("Percentage", ReportCardPdfWriter.pct(d.getPercentage())),
                    new DocumentRegistry.Fact("Grade", d.getGrade() == null ? "-" : d.getGrade()),
                    new DocumentRegistry.Fact("Result", ReportCardPdfWriter.resultText(d.getResult()))));
        }).toList());
        java.util.Map<ReportCardPdfWriter.Card, String> byCard = new java.util.IdentityHashMap<>();
        for (int i = 0; i < published.size(); i++) byCard.put(published.get(i), codes.get(i));
        return cards.stream().map(c -> byCard.containsKey(c)
                ? new ReportCardPdfWriter.Card(c.data(), c.photo(), Verification.of(documentRegistry, byCard.get(c)), c.classTeacher())
                : c).toList();
    }
}
