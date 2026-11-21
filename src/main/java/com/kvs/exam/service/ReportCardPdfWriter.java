package com.kvs.exam.service;

import com.kvs.verify.Verification;
import com.kvs.verify.VerificationBlock;
import com.kvs.core.pdf.PdfDocument;
import com.kvs.core.pdf.PdfDocument.Font;
import com.kvs.core.pdf.PdfDocument.Rgb;
import com.kvs.core.pdf.SchoolLetterhead;
import com.kvs.core.school.SchoolProfile;
import com.kvs.exam.dto.CoScholasticResponse;
import com.kvs.exam.dto.ProgressCardResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.kvs.core.pdf.SchoolLetterhead.*;

/**
 * Report cards (progress cards) on the school letterhead (ms-core): summary band, student and photo, scholastic marks
 * and grades with totals, result and rank, co-scholastic grades, the class teacher's remarks and signature lines.
 * Each student starts on a new page; a long subject list continues on the next page. Cards whose results are not
 * published yet carry a DRAFT watermark.
 */
@Component
public class ReportCardPdfWriter {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);
    private static final Rgb DRAFT = new Rgb(232, 232, 240);

    /** One student's card: the progress card data and the photo (null: an empty photo box) */
    public record Card(ProgressCardResponse data, byte[] photo, Verification verify,
                       SchoolProfile.Signatory classTeacher) {
        public Card(ProgressCardResponse data, byte[] photo) {
            this(data, photo, null, null);
        }

        public Card(ProgressCardResponse data, byte[] photo, Verification verify) {
            this(data, photo, verify, null);
        }
    }

    private static final float ROW = 19;
    /** Lowest y a table row may reach before continuing on a new page (room for the signatures) */
    private static final float LIMIT = PdfDocument.HEIGHT - 190;
    private static final float C_SUBJECT = LEFT + 10, C_MAX = LEFT + 270, C_OBT = LEFT + 340, C_PCT = LEFT + 410, C_GRADE = RIGHT - 10;

    public byte[] write(SchoolLetterhead school, List<Card> cards, Instant generatedAt, ZoneId zone) {
        PdfDocument pdf = new PdfDocument();
        List<String> footers = new ArrayList<>();
        String generated = "Generated " + WHEN.format(generatedAt.atZone(zone));
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) pdf.newPage();
            new CardWriter(pdf, school, cards.get(i), footers, generated).write();
        }
        for (int p = 1; p <= pdf.pageCount(); p++) {
            pdf.onPage(p);
            school.footer(pdf, footers.get(p - 1), p, pdf.pageCount());
        }
        return pdf.toBytes();
    }

    /** Draws one student's card; knows how to continue on a new page */
    private static final class CardWriter {
        private final PdfDocument pdf;
        private final SchoolLetterhead school;
        private final ProgressCardResponse c;
        private final byte[] photo;
        private final Verification verify;
        private final SchoolProfile.Signatory classTeacher;
        private final List<String> footers;
        private final String footer;
        private float y;

        CardWriter(PdfDocument pdf, SchoolLetterhead school, Card card, List<String> footers, String generated) {
            this.pdf = pdf;
            this.school = school;
            this.c = card.data();
            this.photo = card.photo();
            this.verify = card.verify();
            this.classTeacher = card.classTeacher();
            this.footers = footers;
            this.footer = "Report card  |  " + nz(c.getTermName()) + "  |  " + nz(c.getStudentUid()) + "  |  " + generated;
        }

        void write() {
            startPage(false);
            summary();
            student();
            scholastic();
            coScholastic();
            remarks();
            signatures();
        }

        /** The current page belongs to this card: watermark first (under the content), then the letterhead */
        private void startPage(boolean continued) {
            footers.add(footer);
            if (!c.isPublished()) {
                pdf.textRotated(Font.BOLD, 130, DRAFT, 110, PdfDocument.HEIGHT - 220, 35, "DRAFT");
            }
            String note = c.getAcademicYear() == null ? null : "Academic year " + c.getAcademicYear();
            y = school.draw(pdf, "REPORT CARD", continued ? nz(c.getStudentName()) + " (continued)" : note);
        }

        private void newPage() {
            pdf.newPage();
            startPage(true);
        }

        private void summary() {
            float h = 44, cell = (RIGHT - LEFT) / 4;
            pdf.fill(TINT, LEFT, y, RIGHT - LEFT, h);
            String[][] band = {{"EXAMINATION", c.getTermName()}, {"CLASS", c.getClassName()},
                    {"ROLL NO.", c.getRollNumber() == null ? "-" : String.valueOf(c.getRollNumber())}, {"RESULT", resultText(c.getResult())}};
            for (int i = 0; i < band.length; i++) {
                float x = LEFT + 12 + i * cell;
                pdf.text(Font.REGULAR, 7, MUTED, x, y + 16, band[i][0]);
                pdf.text(Font.BOLD, 10.5f, i == 3 ? resultColour(c.getResult()) : INK, x, y + 32,
                        PdfDocument.fit(Font.BOLD, 10.5f, nz(band[i][1]), cell - 16));
            }
            y += h + 22;
        }

        private void student() {
            float photoW = 74, photoH = 90, photoX = RIGHT - photoW;
            pdf.text(Font.BOLD, 8, BRAND, LEFT, y, "STUDENT");
            pdf.line(RULE, 0.8f, LEFT, y + 5, photoX - 20, y + 5);
            float ry = y + 22;
            String[][] rows = {{"Name", c.getStudentName()}, {"Student ID", c.getStudentUid()}, {"Class & section", c.getClassName()},
                    {"Roll number", c.getRollNumber() == null ? "-" : String.valueOf(c.getRollNumber())}};
            for (String[] r : rows) {
                pdf.text(Font.REGULAR, 9, MUTED, LEFT, ry, r[0]);
                pdf.text(Font.BOLD, r[0].equals("Name") ? 12 : 10, INK, LEFT + 95, ry, PdfDocument.fit(Font.BOLD, 10, nz(r[1]), photoX - 20 - LEFT - 95));
                ry += 18;
            }
            pdf.box(RULE, 1, photoX, y - 6, photoW, photoH);
            if (!pdf.image(photo, photoX + 3, y - 3, photoW - 6, photoH - 6)) {
                pdf.textCentre(Font.REGULAR, 7.5f, MUTED, photoX + photoW / 2, y + photoH / 2 - 4, "Photo");
            }
            y = Math.max(ry, y + photoH) + 14;
        }

        private void scholastic() {
            pdf.text(Font.BOLD, 8, BRAND, LEFT, y, "SCHOLASTIC AREAS");
            y += 10;
            tableHeader();
            List<ProgressCardResponse.SubjectResult> subjects = c.getScholastic() == null ? List.of() : c.getScholastic();
            for (int i = 0; i < subjects.size(); i++) {
                if (y + ROW > LIMIT) {
                    pdf.text(Font.REGULAR, 8, MUTED, LEFT, y + 14, "Continued on the next page");
                    newPage();
                    tableHeader();
                }
                ProgressCardResponse.SubjectResult s = subjects.get(i);
                if (i % 2 == 1) pdf.fill(ZEBRA, LEFT, y, RIGHT - LEFT, ROW);
                float base = y + 13;
                boolean below = s.isEntered() && !s.isAbsent() && !s.isPassed();
                pdf.text(Font.REGULAR, 9, INK, C_SUBJECT, base, PdfDocument.fit(Font.REGULAR, 9, nz(s.getSubjectName()), C_MAX - C_SUBJECT - 40));
                pdf.textRight(Font.REGULAR, 9, MUTED, C_MAX, base, num(s.getMaxMarks()));
                pdf.textRight(Font.BOLD, 9, below ? RED : INK, C_OBT, base, !s.isEntered() ? "-" : s.isAbsent() ? "AB" : num(s.getMarksObtained()));
                pdf.textRight(Font.REGULAR, 9, below ? RED : INK, C_PCT, base, s.isEntered() && !s.isAbsent() ? pct(s.getPercentage()) : "-");
                pdf.textRight(Font.BOLD, 9, INK, C_GRADE, base, nz(s.getGrade()));
                y += ROW;
            }
            pdf.line(INK, 0.8f, LEFT, y, RIGHT, y);
            float base = y + 14;
            pdf.text(Font.BOLD, 9.5f, INK, C_SUBJECT, base, "Total");
            pdf.textRight(Font.BOLD, 9.5f, INK, C_MAX, base, num(c.getTotalMax()));
            pdf.textRight(Font.BOLD, 9.5f, INK, C_OBT, base, num(c.getTotalObtained()));
            pdf.textRight(Font.BOLD, 9.5f, INK, C_PCT, base, pct(c.getPercentage()));
            pdf.textRight(Font.BOLD, 9.5f, INK, C_GRADE, base, nz(c.getGrade()));
            y += 22;
            pdf.line(RULE, 0.8f, LEFT, y, RIGHT, y);
            y += 15;
            List<String> facts = new ArrayList<>();
            facts.add("Result: " + resultText(c.getResult()));
            if (c.getGradePoint() != null && "CBSE_GRADES".equals(c.getReportLayout())) facts.add("Grade point: " + num(c.getGradePoint()));
            if (c.getSectionRank() != null) facts.add("Rank in section: " + c.getSectionRank() + " of " + c.getSectionSize());
            if (c.getPassPercent() != null) facts.add("Pass mark: " + pct(c.getPassPercent()));
            pdf.text(Font.REGULAR, 8.5f, MUTED, LEFT, y, String.join("   |   ", facts));
            y += 20;
        }

        private void tableHeader() {
            pdf.fill(BRAND, LEFT, y, RIGHT - LEFT, ROW);
            float base = y + 13;
            pdf.text(Font.BOLD, 8.5f, WHITE, C_SUBJECT, base, "Subject");
            pdf.textRight(Font.BOLD, 8.5f, WHITE, C_MAX, base, "Max marks");
            pdf.textRight(Font.BOLD, 8.5f, WHITE, C_OBT, base, "Obtained");
            pdf.textRight(Font.BOLD, 8.5f, WHITE, C_PCT, base, "Percent");
            pdf.textRight(Font.BOLD, 8.5f, WHITE, C_GRADE, base, "Grade");
            y += ROW;
        }

        private void coScholastic() {
            List<CoScholasticResponse.AreaGrade> areas = c.getCoScholastic() == null ? List.of() : c.getCoScholastic();
            if (areas.isEmpty()) return;
            if (y + 40 + ROW * 2 > LIMIT) newPage();
            pdf.text(Font.BOLD, 8, BRAND, LEFT, y, "CO-SCHOLASTIC AREAS");
            y += 10;
            pdf.fill(TINT, LEFT, y, RIGHT - LEFT, ROW);
            pdf.text(Font.BOLD, 8.5f, INK, C_SUBJECT, y + 13, "Area");
            pdf.text(Font.BOLD, 8.5f, INK, LEFT + 230, y + 13, "Grade");
            pdf.text(Font.BOLD, 8.5f, INK, LEFT + 290, y + 13, "Remarks");
            y += ROW;
            for (CoScholasticResponse.AreaGrade a : areas) {
                if (y + ROW > LIMIT) {
                    newPage();
                }
                float base = y + 13;
                pdf.text(Font.REGULAR, 9, INK, C_SUBJECT, base, PdfDocument.fit(Font.REGULAR, 9, nz(a.getAreaLabel() == null ? a.getArea() : a.getAreaLabel()), 210));
                pdf.text(Font.BOLD, 9, INK, LEFT + 230, base, nz(a.getGrade()));
                pdf.text(Font.REGULAR, 8.5f, MUTED, LEFT + 290, base, PdfDocument.fit(Font.REGULAR, 8.5f, nz(a.getRemarks()), RIGHT - LEFT - 300));
                pdf.line(RULE, 0.5f, LEFT, y + ROW, RIGHT, y + ROW);
                y += ROW;
            }
            y += 16;
        }

        private void remarks() {
            if (y + 70 > LIMIT) newPage();
            pdf.text(Font.BOLD, 8, BRAND, LEFT, y, "CLASS TEACHER'S REMARKS");
            pdf.line(RULE, 0.8f, LEFT, y + 5, RIGHT, y + 5);
            y += 20;
            String text = c.getTeacherRemarks() == null || c.getTeacherRemarks().isBlank() ? "-" : c.getTeacherRemarks();
            y = pdf.paragraph(Font.REGULAR, 9.5f, INK, LEFT, y, RIGHT - LEFT, 13, 5, text);
            if (c.getRemarksBy() != null && !c.getRemarksBy().isBlank()) {
                pdf.textRight(Font.REGULAR, 8, MUTED, RIGHT, y + 2, "- " + c.getRemarksBy());
                y += 12;
            }
        }

        private void signatures() {
            // signatures above the verification QR code at the bottom of the last page
            float sig = Math.max(y + 46, PdfDocument.HEIGHT - 158);
            if (sig > PdfDocument.HEIGHT - 158) {
                newPage();
                sig = PdfDocument.HEIGHT - 158;
            }
            // the class teacher's signature from hrservice, the principal's from Settings > Signatures; the parent signs by hand
            float w = (RIGHT - LEFT - 40) / 3;
            SchoolLetterhead.signedBy(pdf, classTeacher, "Class teacher", LEFT, sig, w);
            school.signature(pdf, SchoolProfile.PRINCIPAL, "Principal", LEFT + w + 20, sig, w);
            SchoolLetterhead.signLine(pdf, "Parent / guardian", LEFT + 2 * (w + 20), sig, w);
            VerificationBlock.draw(pdf, verify);
        }
    }

    static String resultText(String result) {
        if (result == null) return "-";
        return switch (result) {
            case "PASS" -> "Pass";
            case "FAIL" -> "Needs support";
            case "INCOMPLETE" -> "Incomplete";
            default -> result;
        };
    }

    private static Rgb resultColour(String result) {
        return "PASS".equals(result) ? GREEN : "FAIL".equals(result) ? RED : MUTED;
    }

    static String num(BigDecimal v) {
        return v == null ? "-" : v.stripTrailingZeros().toPlainString();
    }

    static String pct(BigDecimal v) {
        return v == null ? "-" : v.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
