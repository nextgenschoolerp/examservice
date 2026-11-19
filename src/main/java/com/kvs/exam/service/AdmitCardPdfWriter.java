package com.kvs.exam.service;

import com.kvs.verify.Verification;
import com.kvs.verify.VerificationBlock;
import com.kvs.core.pdf.PdfDocument;
import com.kvs.core.pdf.PdfDocument.Font;
import com.kvs.core.pdf.SchoolLetterhead;
import com.kvs.core.school.SchoolProfile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static com.kvs.core.pdf.SchoolLetterhead.*;

/**
 * Admit cards (hall tickets) on the school letterhead (ms-core), one A4 page per student: the card summary, the
 * student's details and photo, the date sheet of the class, instructions and signature lines.
 */
@Component
public class AdmitCardPdfWriter {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);

    /** The exam the cards are for */
    public record Exam(String termName, String sessionName, String className, LocalDate startDate, LocalDate endDate,
                       SchoolProfile.Signatory classTeacher) {
        public Exam(String termName, String sessionName, String className, LocalDate startDate, LocalDate endDate) {
            this(termName, sessionName, className, startDate, endDate, null);
        }
    }

    /** One paper of the date sheet; date and times may be null (not scheduled yet) */
    public record Paper(String subjectCode, String subjectName, BigDecimal maxMarks, LocalDate date, LocalTime start, LocalTime end) { }

    /** One student's card; {@code room} is null when none is set, {@code seat} the seat (the roll number by default) */
    public record Card(String cardNumber, String studentName, String studentUid, Integer rollNumber, LocalDate dateOfBirth, byte[] photo,
                       Verification verify, String room, String seat) {
        public Card(String cardNumber, String studentName, String studentUid, Integer rollNumber, LocalDate dateOfBirth, byte[] photo) {
            this(cardNumber, studentName, studentUid, rollNumber, dateOfBirth, photo, null, null, null);
        }

        public Card(String cardNumber, String studentName, String studentUid, Integer rollNumber, LocalDate dateOfBirth, byte[] photo,
                    Verification verify) {
            this(cardNumber, studentName, studentUid, rollNumber, dateOfBirth, photo, verify, null, null);
        }
    }

    static final List<String> INSTRUCTIONS = List.of(
            "Bring this admit card to every paper. It may be checked at the entrance to the examination hall.",
            "Be in the examination hall 15 minutes before the paper begins.",
            "Mobile phones, smart watches and other electronic devices are not allowed in the hall.",
            "Write your roll number on the answer sheet and on every additional sheet.",
            "If this card is lost, ask the school office for a duplicate before the next paper.");

    private static final float COL_DATE = LEFT + 10, COL_DAY = LEFT + 92, COL_TIME = LEFT + 130, COL_CODE = LEFT + 270,
            COL_SUBJECT = LEFT + 330, COL_MAX = RIGHT - 10;
    private static final float ROW = 20;

    public byte[] write(SchoolLetterhead school, Exam exam, List<Paper> papers, List<Card> cards, Instant generatedAt, ZoneId zone) {
        return write(school, exam, papers, cards, generatedAt, zone, INSTRUCTIONS);
    }

    /** The card with the school's own instructions (one per line); the standard ones when the list is empty */
    public byte[] write(SchoolLetterhead school, Exam exam, List<Paper> papers, List<Card> cards, Instant generatedAt, ZoneId zone,
                        List<String> instructions) {
        List<String> lines = instructions == null || instructions.isEmpty() ? INSTRUCTIONS : instructions;
        PdfDocument pdf = new PdfDocument();
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) pdf.newPage();
            card(pdf, school, exam, papers, cards.get(i), lines);
        }
        String generated = "Generated " + WHEN.format(generatedAt.atZone(zone));
        for (int p = 1; p <= pdf.pageCount(); p++) {
            pdf.onPage(p);
            Card c = cards.get(p - 1);
            school.footer(pdf, "Admit card " + c.cardNumber() + "  |  " + exam.termName() + "  |  " + generated, p, pdf.pageCount());
        }
        return pdf.toBytes();
    }

    private void card(PdfDocument pdf, SchoolLetterhead school, Exam exam, List<Paper> papers, Card c, List<String> lines) {
        float y = school.draw(pdf, "ADMIT CARD", exam.sessionName() == null || exam.sessionName().isBlank() ? null : "Academic year " + exam.sessionName());

        // --- summary band
        float bandH = 44, cell = (RIGHT - LEFT) / 4;
        pdf.fill(TINT, LEFT, y, RIGHT - LEFT, bandH);
        String period = exam.startDate() == null ? "-" : DAY.format(exam.startDate())
                + (exam.endDate() == null || exam.endDate().equals(exam.startDate()) ? "" : " - " + DAY.format(exam.endDate()));
        String[][] band = {{"CARD NO.", c.cardNumber()}, {"EXAMINATION", exam.termName()}, {"CLASS", exam.className()}, {"DATES", period}};
        for (int i = 0; i < band.length; i++) {
            float x = LEFT + 12 + i * cell;
            float w = i == 3 ? cell + 10 : cell - 16;
            pdf.text(Font.REGULAR, 7, MUTED, x, y + 16, band[i][0]);
            pdf.text(Font.BOLD, i == 3 ? 9 : 10.5f, INK, x, y + 32, PdfDocument.fit(Font.BOLD, i == 3 ? 9 : 10.5f, band[i][1], w));
        }
        y += bandH + 24;

        // --- student details and photo
        float photoW = 92, photoH = 112, photoX = RIGHT - photoW;
        pdf.text(Font.BOLD, 8, BRAND, LEFT, y, "CANDIDATE");
        pdf.line(RULE, 0.8f, LEFT, y + 5, photoX - 20, y + 5);
        float ry = y + 22;
        String[][] rows = {
                {"Name", c.studentName()},
                {"Student ID", c.studentUid()},
                {"Roll number", c.rollNumber() == null ? "-" : String.valueOf(c.rollNumber())},
                {"Class & section", exam.className()},
                {"Date of birth", c.dateOfBirth() == null ? "-" : DAY.format(c.dateOfBirth())},
                {"Room", c.room() == null ? "To be announced" : c.room()},
                {"Seat number", c.seat() == null ? "-" : c.seat()}};
        for (String[] r : rows) {
            pdf.text(Font.REGULAR, 9, MUTED, LEFT, ry, r[0]);
            pdf.text(Font.BOLD, r[0].equals("Name") ? 12 : 10, INK, LEFT + 95, ry,
                    PdfDocument.fit(Font.BOLD, 10, nz(r[1]), photoX - 20 - LEFT - 95));
            ry += 19;
        }
        pdf.box(RULE, 1, photoX, y - 6, photoW, photoH);
        if (!pdf.image(c.photo(), photoX + 3, y - 3, photoW - 6, photoH - 6)) {
            pdf.textCentre(Font.REGULAR, 8, MUTED, photoX + photoW / 2, y + photoH / 2 - 8, "Paste a recent");
            pdf.textCentre(Font.REGULAR, 8, MUTED, photoX + photoW / 2, y + photoH / 2 + 4, "passport-size photo");
        }
        y = Math.max(ry, y + photoH) + 20;

        // --- date sheet
        pdf.text(Font.BOLD, 8, BRAND, LEFT, y, "DATE SHEET");
        y += 10;
        pdf.fill(BRAND, LEFT, y, RIGHT - LEFT, ROW);
        float base = y + 13.5f;
        pdf.text(Font.BOLD, 8.5f, WHITE, COL_DATE, base, "Date");
        pdf.text(Font.BOLD, 8.5f, WHITE, COL_DAY, base, "Day");
        pdf.text(Font.BOLD, 8.5f, WHITE, COL_TIME, base, "Time");
        pdf.text(Font.BOLD, 8.5f, WHITE, COL_CODE, base, "Code");
        pdf.text(Font.BOLD, 8.5f, WHITE, COL_SUBJECT, base, "Subject");
        pdf.textRight(Font.BOLD, 8.5f, WHITE, COL_MAX, base, "Max marks");
        y += ROW;
        for (int i = 0; i < papers.size(); i++) {
            Paper p = papers.get(i);
            if (i % 2 == 1) pdf.fill(ZEBRA, LEFT, y, RIGHT - LEFT, ROW);
            base = y + 13.5f;
            if (p.date() == null) {
                pdf.text(Font.REGULAR, 9, MUTED, COL_DATE, base, "To be announced");
            } else {
                pdf.text(Font.BOLD, 9, INK, COL_DATE, base, DAY.format(p.date()));
                pdf.text(Font.REGULAR, 9, INK, COL_DAY, base, WEEKDAY.format(p.date()));
                pdf.text(Font.REGULAR, 9, INK, COL_TIME, base, p.start() == null ? "-" : TIME.format(p.start()) + " - " + TIME.format(p.end()));
            }
            pdf.text(Font.REGULAR, 9, MUTED, COL_CODE, base, PdfDocument.fit(Font.REGULAR, 9, p.subjectCode(), COL_SUBJECT - COL_CODE - 6));
            pdf.text(Font.REGULAR, 9, INK, COL_SUBJECT, base, PdfDocument.fit(Font.REGULAR, 9, p.subjectName(), COL_MAX - 55 - COL_SUBJECT));
            pdf.textRight(Font.REGULAR, 9, INK, COL_MAX, base, p.maxMarks() == null ? "-" : p.maxMarks().stripTrailingZeros().toPlainString());
            y += ROW;
        }
        pdf.line(INK, 0.8f, LEFT, y, RIGHT, y);
        y += 26;

        // --- instructions
        pdf.text(Font.BOLD, 8, BRAND, LEFT, y, "INSTRUCTIONS");
        y += 16;
        for (int i = 0; i < lines.size(); i++) {
            pdf.text(Font.REGULAR, 8.5f, MUTED, LEFT, y, (i + 1) + ".");
            y = pdf.paragraph(Font.REGULAR, 8.5f, INK, LEFT + 14, y, RIGHT - LEFT - 14, 11, 2, lines.get(i)) + 3;
        }

        // --- signatures, near the bottom
        // the student signs by hand; the class teacher's signature from hrservice; the exam controller (else the
        // principal) from Settings > Signatures
        // signatures above the verification QR code at the bottom of the page
        float sig = Math.max(y + 46, PdfDocument.HEIGHT - 158);
        float w = (RIGHT - LEFT - 40) / 3;
        SchoolLetterhead.signLine(pdf, "Student's signature", LEFT, sig, w);
        SchoolLetterhead.signedBy(pdf, exam.classTeacher(), "Class teacher's signature", LEFT + w + 20, sig, w);
        if (school.hasSignatory(SchoolProfile.EXAMS)) {
            school.signature(pdf, SchoolProfile.EXAMS, "Controller of examinations", LEFT + 2 * (w + 20), sig, w);
        } else {
            school.signature(pdf, SchoolProfile.PRINCIPAL, "Principal's signature", LEFT + 2 * (w + 20), sig, w);
        }
        VerificationBlock.draw(pdf, c.verify());
    }

    /** "PT2" for "Periodic Test 2", "HY" for "Half-Yearly": the card-number prefix */
    static String termCode(String termName) {
        StringBuilder s = new StringBuilder();
        for (String w : (termName == null ? "" : termName).split("[\\s\\-_]+")) {
            if (w.isEmpty()) continue;
            if (Character.isDigit(w.charAt(0))) s.append(w);
            else if (Character.isLetter(w.charAt(0))) s.append(Character.toUpperCase(w.charAt(0)));
        }
        return s.isEmpty() ? "EX" : s.toString();
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
