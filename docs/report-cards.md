# Report card PDF (examservice)

Status: **done** (18 Nov 2026). Nothing is committed yet. Roadmap item B3 in `coreservice/docs/school-pdf-documents.md`.

## Goal

The progress card staff and parents already see on screen (`GET /api/v1/exams/students/{id}/progress-card`) as a
printable PDF on the school letterhead (ms-core `com.kvs.core.pdf`): for one student, and for a whole section in one
file for printing.

## Design

- **Same data, same rules.** The PDF is drawn from `ExamResultService.progressCard(studentId, termId)`, so marks,
  grades, ranks and the parent rule are the screen's: a parent gets the card only once it is published (404 before).
- `GET /api/v1/exams/students/{studentId}/progress-card/pdf?examTermId=` (permission `exam.students.view`: staff,
  and a parent for their own child)
- `GET /api/v1/exams/terms/{examTermId}/report-cards?sectionId=[&branchId=]` (permission `exam.terms.view`, CLASS scope:
  a class teacher prints their own sections) → one PDF, each student starting on a new page, by roll number; header
  `X-Report-Cards` with the count. 400 when the class has no subjects in the term.
- Each card:
  - letterhead with "REPORT CARD" and the academic year
  - summary band: examination, class, roll number, result
  - student: name, student ID, class and section, roll number, and the photo from studentservice
  - **scholastic** table: subject, maximum marks, marks obtained ("AB" when absent, "-" when not entered), percentage,
    grade; marks below the pass mark in red
  - totals: marks, percentage, grade (and grade point for CBSE layouts), result, rank in section
  - **co-scholastic** table: area, grade, remarks (left out when the term has none)
  - class teacher's remarks
  - signature lines: class teacher, principal, parent
  - **DRAFT** watermark while the results are not published (staff may print it to check before publishing)

## Steps

- [x] 1. `ReportCardPdfWriter` on the ms-core letterhead (several pages when the subjects do not fit)
- [x] 2. `ReportCardService` and the two endpoints
- [x] 3. Tests: content, DRAFT before publishing, a parent before and after publishing, another family's child,
      section file, RBAC parity
- [x] 4. RBAC catalogue, web app rules, API mapping document
- [x] 5. Web app: **Download PDF** in the progress card window (one student), **Report cards (PDF)** on the
      Broadsheet tab (a section)
- [x] 6. Check live: a DPS student's card and a section file

## Results

- examservice: 25 tests pass: the integration test (DRAFT before publishing, a parent 404 before and the card after
  publishing, another family's child 404, the section file by roll number, missing parameters 400), 3 writer tests
  (layout, a 22-subject card continuing on a second page, result wording) and the RBAC parity test.
- Web app: 32 tests pass and the build compiles. **Report cards (PDF)** on the Broadsheet tab and **Download PDF** in
  the progress card window.
- Live (DPS North, Half-Yearly, Class 1-A): 3 cards with the crest and photos, no DRAFT (published), Krish Pandey's
  card shows Pass and rank 1 of 3.

## Later

- Attendance for the term on the card (attendance lives in studentservice; needs a call or a copy)
- Parent app download (roadmap D4)
