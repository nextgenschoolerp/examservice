package com.kvs.exam.service;

import com.kvs.core.pdf.SchoolLetterhead;
import com.kvs.core.school.SchoolProfile;
import com.kvs.exam.dto.CoScholasticResponse;
import com.kvs.exam.dto.ProgressCardResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The report card layout: summary band, student, scholastic table and totals, co-scholastic, remarks, signatures. */
class ReportCardPdfWriterTest {

    private static final SchoolLetterhead SCHOOL = SchoolLetterhead.from(new SchoolProfile("Delhi Public School", "North Campus",
            "Plot 14, Sector 21, Rohini, New Delhi 110085", "+91 11 2758 4400", "office@dps.edu.in", "www.dps.edu.in", "CBSE-2730012", null));

    @Test
    void printsTheProgressCard() throws Exception {
        byte[] pdf = write(card(true, 6));
        String text = new String(pdf, StandardCharsets.ISO_8859_1);
        assertThat(text).contains("/Count 1", "(REPORT CARD) Tj", "(Academic year 2026-2027) Tj", "(Aarav Sharma) Tj", "(Half-Yearly) Tj",
                "(Class 7-B) Tj", "(Pass) Tj", "(English) Tj", "(AB) Tj", "(A1) Tj", "(Total) Tj", "(480) Tj", "(81.3%) Tj",
                "Rank in section: 3 of 38", "Grade point: 9", "(Art education) Tj", "Enthusiastic in class discussions", "(Principal) Tj")
                .doesNotContain("(DRAFT) Tj");
        save("report-card-sample.pdf", pdf);
    }

    @Test
    void continuesALongCardAndMarksUnpublishedOnesDraft() throws Exception {
        byte[] pdf = write(card(false, 22));
        String text = new String(pdf, StandardCharsets.ISO_8859_1);
        assertThat(text).contains("/Count 2", "Continued on the next page", "(Aarav Sharma \\(continued\\)) Tj", "(DRAFT) Tj", "Page 2 of 2");
        save("report-card-long-draft-sample.pdf", pdf);
    }

    @Test
    void wordsTheResultLikeTheApp() {
        assertThat(ReportCardPdfWriter.resultText("FAIL")).isEqualTo("Needs support");
        assertThat(ReportCardPdfWriter.resultText("INCOMPLETE")).isEqualTo("Incomplete");
        assertThat(ReportCardPdfWriter.pct(new BigDecimal("93.333"))).isEqualTo("93.3%");
        assertThat(ReportCardPdfWriter.num(new BigDecimal("80.00"))).isEqualTo("80");
    }

    private static byte[] write(ProgressCardResponse c) {
        return new ReportCardPdfWriter().write(SCHOOL, List.of(new ReportCardPdfWriter.Card(c, null)),
                Instant.parse("2026-11-18T06:30:00Z"), ZoneId.of("Asia/Kolkata"));
    }

    private static ProgressCardResponse card(boolean published, int subjects) {
        String[] names = {"English", "Hindi", "Mathematics", "Science", "Social Science", "Computer Science"};
        List<ProgressCardResponse.SubjectResult> rows = new ArrayList<>();
        for (int i = 0; i < subjects; i++) {
            boolean absent = i == 4;
            rows.add(ProgressCardResponse.SubjectResult.builder().subjectCode("S" + i).subjectName(names[i % names.length] + (i >= names.length ? " " + (i / names.length + 1) : ""))
                    .maxMarks(new BigDecimal("80")).entered(true).absent(absent).marksObtained(absent ? null : new BigDecimal(60 + i % 20))
                    .percentage(absent ? null : new BigDecimal(75 + i % 20)).grade(absent ? null : i % 3 == 0 ? "A1" : "A2").passed(!absent).build());
        }
        return ProgressCardResponse.builder().studentUid("STU-2024-0142").studentName("Aarav Sharma").className("Class 7-B").rollNumber(14)
                .termName("Half-Yearly").academicYear("2026-2027").reportLayout("CBSE_GRADES").passPercent(new BigDecimal("33"))
                .scholastic(rows).totalObtained(new BigDecimal("390")).totalMax(new BigDecimal("480")).percentage(new BigDecimal("81.25"))
                .grade("A2").gradePoint(new BigDecimal("9")).result("PASS").sectionRank(3).sectionSize(38)
                .coScholastic(List.of(
                        CoScholasticResponse.AreaGrade.builder().area("ART_EDUCATION").areaLabel("Art education").grade("A").remarks("Creative use of colour").build(),
                        CoScholasticResponse.AreaGrade.builder().area("HEALTH_AND_PHYSICAL_EDUCATION").areaLabel("Health & physical education").grade("A+").build()))
                .teacherRemarks("Enthusiastic in class discussions and consistent with homework. Should read more widely in English.")
                .remarksBy("Ms. Meera Nair").published(published).build();
    }

    private static void save(String name, byte[] pdf) throws Exception {
        Path dir = Path.of("build", "pdf-samples");
        Files.createDirectories(dir);
        Files.write(dir.resolve(name), pdf);
    }
}
