package com.kvs.exam.service;

import com.kvs.core.pdf.SchoolLetterhead;
import com.kvs.core.school.SchoolProfile;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The admit card layout: letterhead, candidate and photo, date sheet, instructions, signatures; one page per student. */
class AdmitCardPdfWriterTest {

    private static final SchoolLetterhead SCHOOL = SchoolLetterhead.from(new SchoolProfile("Delhi Public School", "North Campus",
            "Plot 14, Sector 21, Rohini, New Delhi 110085", "+91 11 2758 4400", "office@dps.edu.in", "www.dps.edu.in", "CBSE-2730012", null));
    private static final AdmitCardPdfWriter.Exam EXAM = new AdmitCardPdfWriter.Exam("Periodic Test 2", "2026-2027", "Class 7-B",
            LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 21));
    private static final List<AdmitCardPdfWriter.Paper> PAPERS = List.of(
            paper("ENG", "English", 16, "09:30", "11:30"), paper("MATH", "Mathematics", 17, "09:30", "11:30"),
            paper("SCI", "Science", 18, "09:30", "11:30"), paper("SST", "Social Science", 19, "09:30", "11:30"),
            paper("HIN", "Hindi", 20, "09:30", "11:00"), new AdmitCardPdfWriter.Paper("COMP", "Computer Science", new BigDecimal("50.00"), null, null, null));

    @Test
    void printsOneCardPerStudentWithTheDateSheet() throws Exception {
        byte[] pdf = new AdmitCardPdfWriter().write(SCHOOL, EXAM, PAPERS, List.of(
                new AdmitCardPdfWriter.Card("PT2-STU-2024-0142", "Aarav Sharma", "STU-2024-0142", 14, LocalDate.of(2013, 8, 2), photo()),
                new AdmitCardPdfWriter.Card("PT2-STU-2024-0150", "Diya Verma", "STU-2024-0150", 15, null, null)),
                Instant.parse("2026-11-18T06:30:00Z"), ZoneId.of("Asia/Kolkata"));
        String text = new String(pdf, StandardCharsets.ISO_8859_1);

        assertThat(text).contains("/Count 2", "(ADMIT CARD) Tj", "(Academic year 2026-2027) Tj", "(PT2-STU-2024-0142) Tj",
                "(Aarav Sharma) Tj", "(Diya Verma) Tj", "(16 Nov 2026) Tj", "(Mon) Tj", "(09:30 AM - 11:30 AM) Tj", "(Social Science) Tj",
                "(To be announced) Tj", "(50) Tj", "(16 Nov 2026 - 21 Nov 2026) Tj", "Student's signature", "Page 2 of 2",
                "/Subtype /Image", "passport-size photo");
        Path dir = Path.of("build", "pdf-samples");
        Files.createDirectories(dir);
        Files.write(dir.resolve("admit-card-sample.pdf"), pdf);
    }

    @Test
    void cardNumbersStartWithTheTermsInitials() {
        assertThat(AdmitCardPdfWriter.termCode("Periodic Test 2")).isEqualTo("PT2");
        assertThat(AdmitCardPdfWriter.termCode("Half-Yearly")).isEqualTo("HY");
        assertThat(AdmitCardPdfWriter.termCode("Annual")).isEqualTo("A");
        assertThat(AdmitCardPdfWriter.termCode("")).isEqualTo("EX");
    }

    private static AdmitCardPdfWriter.Paper paper(String code, String name, int day, String start, String end) {
        return new AdmitCardPdfWriter.Paper(code, name, new BigDecimal("80.00"), LocalDate.of(2026, 11, day), LocalTime.parse(start), LocalTime.parse(end));
    }

    /** A plain portrait, as a student photo would be */
    private static byte[] photo() throws Exception {
        BufferedImage img = new BufferedImage(280, 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0xdfe6f0));
        g.fillRect(0, 0, 280, 360);
        g.setColor(new Color(0x6b7a90));
        g.fillOval(85, 60, 110, 130);
        g.fillOval(40, 210, 200, 220);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }
}
