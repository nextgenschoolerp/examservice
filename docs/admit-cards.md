# Date sheet and admit cards (examservice)

Status: **done** (18 Nov 2026). Nothing is committed yet.

## Goal

Print an admit card (hall ticket) for every student of a section, or for one student, for an exam term. The card is
on the school letterhead from ms-core (`com.kvs.core.pdf`, see `coreservice/docs/school-pdf-documents.md`) and lists
the term's papers with their date and time.

Exam subjects had marks but no dates, so the change first adds a **date sheet**: a date, start time and end time for
each subject of a class in a term.

## Design

### Date sheet

- Three nullable columns on `exam_term_subjects`: `exam_date date`, `start_time time`, `end_time time`.
- `GET /api/v1/exams/terms/{examTermId}/subjects` returns them (`examDate`, `startTime`, `endTime`).
- `PUT /api/v1/exams/terms/{examTermId}/date-sheet` (permission `exam.templates.manage`, the exam controller) sets
  them for one class:
  `{ gradeId, entries: [{ subjectCode, examDate, startTime, endTime }] }`. Rules: the subject must be configured for
  the class; the date must fall within the term's start and end dates; start and end time come together and end is
  after start; a null date clears the entry. Separate from `POST …/subjects`, so editing marks setup never clears dates.

### Admit cards

- `GET /api/v1/exams/terms/{examTermId}/admit-cards?sectionId=…[&branchId=…]` (permission `exam.terms.view`, CLASS
  scope: a class teacher prints their own sections) → one PDF, one card per A4 page, students by roll number.
- `GET /api/v1/exams/students/{studentId}/admit-card?examTermId=…` (permission `exam.students.view`, OWN scope: a
  parent downloads their own child's card). A separate path, so each endpoint has one permission in the RBAC catalogue.
- Students **on hold** (`student_master.is_on_hold`, for example fee dues) get no card. In a section PDF they are left
  out and counted in the `X-Admit-Cards-Withheld` header; for a single student the answer is 409 with the reason.
- Refused (400) when the class has no subjects for the term, or none of them has a date yet.
- Each card shows:
  - the letterhead with "ADMIT CARD", the term name and academic session
  - the student's photo (from studentservice, read with the user's token; a "Paste photo" box when there is none)
  - name, student ID, roll number, class and section, date of birth, a card number (`<term>-<studentUid>`)
  - the **room** of the class for the exam ("To be announced" when none is set) and the student's **seat number** (the
    roll number unless a seat was set by hand), from Exam & Assessment → Date sheet & admit cards → **Seating**
    (`exam_term_rooms`, `exam_seats`; `GET /api/v1/exams/terms/{id}/seating?sectionId=`, `PUT …/sections/{sectionId}/room`,
    `PUT …/seats`; `exam.templates.manage` to change, `exam.terms.view` to read)
  - the date sheet: date, day, time, subject code, subject, maximum marks (papers without a date show "To be
    announced")
  - the instructions: the school's own from Settings → General → **Instructions** (one per line, up to 8 lines of 200
    characters; saved by `PUT /api/v1/tenants/{id}/school-profile/instructions`), or the standard five in
    `AdmitCardPdfWriter.INSTRUCTIONS` when the school has set none; read by examservice with the school profile
    (`SchoolProfile.instructionLines()`)
  - signature lines for the student, class teacher and principal (or exam controller), and the QR verification block

## Steps

- [x] 1. Database: migration, `03_tenant_schema.sql`, `module05_exam/create.sql` and `update.sql`; applied to every school
- [x] 2. Entity and DTOs: `ExamSubject`, `ExamSubjectResponse`, new `SaveDateSheetRequest`
- [x] 3. Date sheet endpoint with its rules
- [x] 4. Admit card PDF writer on the ms-core letterhead, student photo client
- [x] 5. Admit card endpoint (section and single student, on-hold students withheld)
- [x] 6. Tests: date sheet rules, admit card PDF content, withheld students
- [x] 7. RBAC catalogue (`database/rbac/endpoint-permissions.json`), web app rules, API mapping document
- [x] 8. Web app: "Date sheet & admit cards" tab on Exam & Assessment (edit dates, download cards)
- [x] 9. Seed dates for the demo school's Periodic Test 2; check live in the browser

## Results

- examservice: 20 tests pass (3 new integration tests for the date sheet, section cards and single cards, 2 writer
  tests, the RBAC parity test with the three new endpoints listed as deliberate).
- Database: columns added to `dpsdata`, `gmetdata` and `svmtdata`; Periodic Test 2 of DPS North is scheduled
  16-20 Nov 2026, 09:30-11:30 (live and in `04_tenant_data_dps.sql`).
- Web app: tab **Date sheet & admit cards** on Exam & Assessment; 32 tests pass and the build compiles. Saving a
  changed time and generating Class 1-A's cards (3 cards, with photos) were checked live.

## Later

- Done 18 Nov 2026: the staff web app (student profile → Academics → **Admit cards**) and the parent app (Exam
  schedule → **Download Hall Ticket (PDF)**, the card for the next exam; shared on the phone, downloaded on web).
- Done 19 Nov 2026: the exam **room and seat number** on the card (Seating on the Date sheet & admit cards tab; the
  room is per class and exam, the seat per student and exam, blank meaning the roll number) and the school's own
  **instructions** (Settings → General → Instructions). Open: seat numbers are not checked for duplicates across
  students.
