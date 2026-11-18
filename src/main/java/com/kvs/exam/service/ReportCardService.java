package com.kvs.exam.service;

import com.kvs.core.exception.BadRequestException;
import com.kvs.core.pdf.SchoolLetterhead;
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

    @Value("${kvs.school.name:School}") private String schoolName;
    @Value("${kvs.time-zone:Asia/Kolkata}") private String timeZone;

    /** One student's report card (the same access as the progress card on screen) */
    public ReportCards forStudent(UUID examTermId, UUID studentId) {
        ProgressCardResponse card = results.progressCard(studentId, examTermId);
        StudentMaster student = access.student(studentId);
        byte[] pdf = render(student.getTenantId(), List.of(new ReportCardPdfWriter.Card(card, photo(student))));
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
        List<ReportCardPdfWriter.Card> cards = roster.stream()
                .map(s -> new ReportCardPdfWriter.Card(results.progressCard(s.getStudentId(), examTermId), photo(s))).toList();
        byte[] pdf = render(term.getTenantId(), cards);
        return new ReportCards(pdf, "Report-cards-" + AdmitCardPdfWriter.termCode(term.getTermName()) + "-"
                + access.className(section).replace(' ', '-') + ".pdf", cards.size());
    }

    private byte[] photo(StudentMaster s) {
        return s.getProfilePhotoUrl() == null ? null : photos.photo(s.getStudentId());
    }

    private byte[] render(String tenantId, List<ReportCardPdfWriter.Card> cards) {
        SchoolProfile profile = schoolProfiles.getOr(tenantId, SchoolProfile.named(schoolName));
        return writer.write(SchoolLetterhead.from(profile), cards, Instant.now(), ZoneId.of(timeZone));
    }
}
