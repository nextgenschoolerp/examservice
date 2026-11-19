package com.kvs.exam.service;

import com.kvs.verify.Verification;
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
            "Plot 14, Sector 21, Rohini, New Delhi 110085", "+91 11 2758 4400", "office@dps.edu.in", "www.dps.edu.in", "CBSE-2730012", null)
            .withSignatories(java.util.Map.of(SchoolProfile.PRINCIPAL, new SchoolProfile.Signatory("Dr. Anita Rao", "Principal", signaturePng()))));
    private static final AdmitCardPdfWriter.Exam EXAM = new AdmitCardPdfWriter.Exam("Periodic Test 2", "2026-2027", "Class 7-B",
            LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 21),
            new SchoolProfile.Signatory("Ms. Meera Nair", "Class teacher", signaturePng()));
    private static final List<AdmitCardPdfWriter.Paper> PAPERS = List.of(
            paper("ENG", "English", 16, "09:30", "11:30"), paper("MATH", "Mathematics", 17, "09:30", "11:30"),
            paper("SCI", "Science", 18, "09:30", "11:30"), paper("SST", "Social Science", 19, "09:30", "11:30"),
            paper("HIN", "Hindi", 20, "09:30", "11:00"), new AdmitCardPdfWriter.Paper("COMP", "Computer Science", new BigDecimal("50.00"), null, null, null));

    @Test
    void printsOneCardPerStudentWithTheDateSheet() throws Exception {
        byte[] pdf = new AdmitCardPdfWriter().write(SCHOOL, EXAM, PAPERS, List.of(
                new AdmitCardPdfWriter.Card("PT2-STU-2024-0142", "Aarav Sharma", "STU-2024-0142", 14, LocalDate.of(2013, 8, 2), photo(), new com.kvs.verify.Verification("DPS-7F3K-92QA", "https://erp.dps.edu.in/verify/DPS-7F3K-92QA", "erp.dps.edu.in/verify"), "Hall A", "A-14"),
                new AdmitCardPdfWriter.Card("PT2-STU-2024-0150", "Diya Verma", "STU-2024-0150", 15, null, null)),
                Instant.parse("2026-11-18T06:30:00Z"), ZoneId.of("Asia/Kolkata"));
        String text = new String(pdf, StandardCharsets.ISO_8859_1);

        assertThat(text).contains("/Count 2", "(DPS-7F3K-92QA) Tj", "(Ms. Meera Nair) Tj", "(Class teacher) Tj", "(Dr. Anita Rao) Tj", "(Student's signature) Tj", "(ADMIT CARD) Tj", "(Academic year 2026-2027) Tj", "(PT2-STU-2024-0142) Tj",
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

    /** A pen-like scribble on a transparent background, like a scanned signature */
    static byte[] signaturePng() {
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(360, 110, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new java.awt.Color(0x1b2a6b));
            g.setStroke(new java.awt.BasicStroke(5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            java.awt.geom.Path2D.Float path = new java.awt.geom.Path2D.Float();
            path.moveTo(20, 80);
            path.curveTo(40, 10, 70, 10, 75, 70);
            path.curveTo(80, 100, 110, 40, 130, 55);
            path.curveTo(150, 70, 160, 30, 185, 50);
            path.curveTo(205, 65, 220, 35, 245, 55);
            path.curveTo(270, 75, 300, 40, 340, 45);
            g.draw(path);
            g.drawLine(60, 92, 300, 88);
            g.dispose();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
