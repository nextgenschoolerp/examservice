# API Specification: Module 05 — Examinations, Grading & NEP 2020 Assessments

- **Target Microservice**: `examservice` (Port `8086`, context path `/examservice`; test port `18086`)
- **API Base Path**: `/api/v1/exams`
- **Security Standard**: Bearer JWT from authservice, role-based access (`@PreAuthorize`, [`ExamRoles`](../../src/main/java/com/kvs/exam/config/ExamRoles.java)) and tenant isolation (`tenantId` claim); parents are matched on the `userId` claim
- **Traceability**: Micrometer tracing (`X-Trace-Id`, `X-Span-Id` echoed)
- **BRD Specification**: [`story/module5/School_ERP_Module_05_Exams_Assessment_1000.md`](../../../story/module5/School_ERP_Module_05_Exams_Assessment_1000.md), data model [`module5.sql`](../../../story/module5/module5.sql)
- **Postman**: [`postman/School_ERP_ExamService.postman_collection.json`](../../postman/School_ERP_ExamService.postman_collection.json)
- **Seed**: [`studentservice/src/main/resources/db/seed/module05_exam_seed.sql`](../../../studentservice/src/main/resources/db/seed/module05_exam_seed.sql) (next to the Module 03 seed it builds on)

---

## 1. Executive Summary & Capabilities

The first release covers the stories behind the student profile's "Academic progress" and the teacher workflow from setup to published report cards:

1. **US-05000** grading scales, assessment terms (formative / summative, term weightage, T_lock deadline) and per-class subjects with max / pass marks and weightage.
2. **US-05003** web mark entry grid per section and subject (absent supported).
3. **US-05005** T_lock: mark entry closes at the term deadline, or earlier when the exam controller locks it.
4. **US-05006** unlock tickets: a teacher asks, the principal / exam controller approves (reopens the subject for the section for 1–72 h) or rejects.
5. **US-05007** class broadsheet: students × subjects, weighted percentage, grade, PASS / FAIL / INCOMPLETE and rank.
6. **US-05008** co-scholastic grades (art, health & PE, work and value education, discipline: A+ / A / B / C).
7. **US-05009** holistic progress card: scholastic results, grade point, section rank, co-scholastic grades and remarks.
8. **US-05010** publish a section's progress cards to parents (refused while entries are missing).
9. **US-05011** rank lists (competition ranking 1, 2, 2, 4), percentile bands and subject toppers.
10. **US-05013** term-on-term progress trend with change, year-weighted percentage and trend label.
11. **US-05016** incomplete entry report per section and subject.
12. **US-05018** class teacher narrative remarks (locked once published).

Not in this release: seating plans (US-05001), admit cards (US-05002), Excel import (US-05004), self / peer appraisal (US-05012), grace marks (US-05014), LOC XML export (US-05015), failure analysis (US-05017), archiving (US-05019).

### Calculation rules

| Item | Rule |
|---|---|
| Subject % | marks ÷ max × 100 (absent = 0, and fails the subject) |
| Term % | subject percentages weighted by subject weightage |
| Grade | highest band of the term's grading scale whose minimum ≤ % |
| Result | `INCOMPLETE` while a subject has no entry; `PASS` when every subject ≥ pass marks and term % ≥ the scale's pass %; else `FAIL` |
| Rank | competition ranking by term % within the section (rank list: whole class or one section) |
| Percentile band | rank ÷ ranked students ≤ 10% → Top 10%, ≤ 25% → Top 25%, ≤ 50% → Top 50%, else Below median |
| Year-weighted % | Σ(term % × term weightage) ÷ Σ weightage over the latest academic year's terms with results |
| Trend | latest term against the previous: > +2 points IMPROVING, < −2 DECLINING, else STEADY |

### Actors and roles

| BRD actor | Role |
|---|---|
| Exam Controller | `EXAM_CONTROLLER` |
| Subject Teacher, Evaluator | `SUBJECT_TEACHER` |
| Class Teacher | `CLASS_TEACHER` |
| Academic Principal | `SCHOOL_PRINCIPAL` (also `VICE_PRINCIPAL` for reading) |
| Academic Auditor | `ACADEMIC_AUDITOR` |
| Parent | `APPLICANT_PARENT` — own children, published terms only |
| Student | no student login exists yet |

`REGISTRAR` can read terms, results and progress cards; `SUPER_ADMIN` can do everything.

### Data ownership

examservice owns `grading_scales`, `grading_scale_bands`, `exam_terms`, `exam_term_subjects`, `marks_entry_register`, `grade_lock_tickets`, `co_scholastic_grades` and `holistic_progress_cards`. Students, classes, sections and academic years belong to studentservice (Module 03); examservice maps `student_master`, `grades`, `sections` and `academic_sessions` of the shared database read-only to build rosters. Rosters can be narrowed to one campus with `branchId`.

---

## 2. Security & Header Specifications

| Header | Requirement | Description |
|---|---|---|
| `Authorization` | Required | `Bearer <JWT>` from authservice (`roles`, `tenantId`, `userId`) |
| `Content-Type` | POST / PUT / PATCH | `application/json` |
| `X-Trace-Id` / `X-Span-Id` | Optional | Echoed in the response |

| HTTP | `errorCode` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean validation (`validationErrors[]`) |
| 400 | `BAD_REQUEST` | e.g. marks above max, student not on the section roll, missing query parameter, weightage over 100% |
| 401 | `UNAUTHORIZED` | No / invalid token |
| 403 | `ACCESS_DENIED` | Role not allowed |
| 404 | `RESOURCE_NOT_FOUND` | Unknown id, another institution, a parent's request for another family's student or an unpublished card |
| 409 | `RESOURCE_ALREADY_EXISTS` | Duplicate scale / term name; term locked; ticket pending or already decided; deciding your own ticket; publishing with missing entries; remarks after publishing |

---

## 3. Endpoints Breakdown

{expand:title=US-05000: Configure assessment terms, subject weightage ratios, and grading scales}

| Method | Path | Roles | Success |
|---|---|---|---|
| `POST` | `/api/v1/exams/grading-scales` | EXAM_CONTROLLER, SCHOOL_PRINCIPAL | `201` |
| `GET` | `/api/v1/exams/grading-scales` | teaching staff, registrar | `200` |
| `POST` | `/api/v1/exams/terms` | EXAM_CONTROLLER, SCHOOL_PRINCIPAL | `201` |
| `GET` | `/api/v1/exams/terms?academicYearId=` | teaching staff, registrar, parent | `200` |
| `GET` | `/api/v1/exams/terms/{termId}` | same | `200` |
| `POST` | `/api/v1/exams/terms/{termId}/subjects` | EXAM_CONTROLLER, SCHOOL_PRINCIPAL | `201` (existing codes are updated) |
| `GET` | `/api/v1/exams/terms/{termId}/subjects?gradeId=` | teaching staff, registrar | `200` |

Grading scale: `scaleName` (unique), `passPercent` 0–100, `bands[]` (`gradeLabel` ≤ 5, `minPercent` 0–100, `gradePoint` 0–10, `description`); labels and minimums must be distinct and one band must start at 0%.

Term: `academicYearId` (studentservice session), `termName` (unique in the year), `termType` FORMATIVE / SUMMATIVE, `weightagePercent` 1–100 (the year's terms ≤ 100%), `startDate` / `endDate` inside the academic year, `markEntryDeadline` (instant, not before the start), `gradingScaleId`.

```json
{ "academicYearId": "5eed0003-0001-4000-8000-000000000002", "termName": "Half-Yearly", "termType": "SUMMATIVE",
  "weightagePercent": 30, "startDate": "2026-09-14", "endDate": "2026-09-26",
  "markEntryDeadline": "2026-10-10T12:30:00Z", "gradingScaleId": "5eed0005-0001-4000-8000-000000000001" }
```

Subjects: `gradeId` + `subjects[]` (`subjectCode` `^[A-Z][A-Z0-9_]{1,19}$`, `subjectName`, `maxMarks` 1–999, `passMarks` ≤ max, `weightage` 0.1–10, default 1).

Term response: `examTermId`, `sessionName`, `termName`, `termType`, `weightagePercent`, dates, `markEntryDeadline`, `locked`, `lockedAt`, `gradingScaleName`, `subjectCount`.

**Audit**: `GRADING_SCALE_CREATED`, `EXAM_TERM_CREATED`, `EXAM_SUBJECTS_CONFIGURED`.

{expand}

{expand:title=US-05003: Enter student scholastic scores via responsive web mark entry grids}

| Method | Path | Roles |
|---|---|---|
| `GET` | `/api/v1/exams/terms/{termId}/marks?sectionId=&subjectCode=&branchId=` | teaching staff, registrar |
| `PUT` | `/api/v1/exams/terms/{termId}/marks?branchId=` | SUBJECT_TEACHER, CLASS_TEACHER, EXAM_CONTROLLER |

```json
{ "sectionId": "5eed0003-0003-4000-8000-000000000101", "subjectCode": "ENG",
  "entries": [ { "studentId": "…", "marksObtained": 31 }, { "studentId": "…", "absent": true } ] }
```

Each entry has marks or `absent: true` (not both); marks ≤ the subject maximum; every student must be on the section roll. The response is the refreshed sheet: `className`, `subjectName`, `maxMarks`, `passMarks`, `markEntryDeadline`, `locked`, `lockReason`, `unlockedUntil`, `enteredCount`, `missingCount`, `rows[]` (`studentId`, `studentUid`, `studentName`, `rollNumber`, `marksObtained`, `absent`, `entered`, `evaluatedBy`, `enteredAt`).

**Audit**: `MARKS_ENTERED` (notes the unlock ticket when one was used).

{expand}

{expand:title=US-05005: Lock examination score entry screens automatically upon timer expiration (T_lock)}

A term is locked once `markEntryDeadline` has passed or after `POST /api/v1/exams/terms/{termId}/lock` (EXAM_CONTROLLER, SCHOOL_PRINCIPAL; `409` if already locked). Saving marks on a locked term answers `409` unless an approved ticket keeps that subject open for the section. The manual lock also flags the entered marks `is_locked`.

**Audit**: `EXAM_TERM_LOCKED`.

{expand}

{expand:title=US-05006: Submit formal ticket requests to unlock locked mark sheets for correction}

| Method | Path | Roles |
|---|---|---|
| `POST` | `/api/v1/exams/terms/{termId}/unlock-tickets` | SUBJECT_TEACHER, CLASS_TEACHER |
| `GET` | `/api/v1/exams/terms/{termId}/unlock-tickets` | teaching staff, registrar |
| `PATCH` | `/api/v1/exams/unlock-tickets/{ticketId}/decision` | SCHOOL_PRINCIPAL, EXAM_CONTROLLER |

Request: `{ "sectionId", "subjectCode", "reason" }` — only for a locked term (`409` otherwise), one pending ticket per subject and section. Decision: `{ "decision": "APPROVED" | "REJECTED", "remarks", "unlockHours": 1–72 (default 24) }`; remarks are required to reject; the requester cannot decide their own ticket (`409`); a decided ticket cannot change (`409`).

**Audit**: `MARK_UNLOCK_REQUESTED`, `MARK_UNLOCK_APPROVED`, `MARK_UNLOCK_REJECTED`.

{expand}

{expand:title=US-05007: Compile master broadsheets aggregating formative and summative assessments}

`GET /api/v1/exams/terms/{termId}/broadsheet?sectionId=&branchId=` — teaching staff, registrar. Rows: marks per subject (`AB` for absent), totals, weighted %, grade, result, rank; `subjects[]` with average, highest, passed and failed counts; `classAveragePercent`. Formative and summative terms are combined per student in the progress trend (US-05013).

{expand}

{expand:title=US-05008: Evaluate co-scholastic rubrics covering sports, arts, and value education}

`PUT /api/v1/exams/terms/{termId}/co-scholastic` (CLASS_TEACHER, SUBJECT_TEACHER, EXAM_CONTROLLER) with `entries[]` of `{ studentId, area, grade (A+|A|B|C), remarks }`; areas `ART_EDUCATION`, `HEALTH_AND_PHYSICAL_EDUCATION`, `WORK_EDUCATION`, `VALUE_EDUCATION`, `DISCIPLINE`. `GET …/co-scholastic?sectionId=` lists a section. **Audit**: `CO_SCHOLASTIC_GRADED`.

{expand}

{expand:title=US-05009: Synthesize multi-dimensional data into NEP 2020-compliant holistic progress cards}

`GET /api/v1/exams/students/{studentId}/progress-card?examTermId=` — teaching staff, registrar, parent (own child, published only).

```json
{ "success": true, "data": {
  "studentUid": "STU-2026-00101", "studentName": "Aarav Sharma", "className": "Class 6-A", "termName": "Half-Yearly", "academicYear": "2026-2027",
  "scholastic": [ { "subjectCode": "MATH", "subjectName": "Mathematics", "maxMarks": 80, "marksObtained": "…", "percentage": "…", "grade": "…", "passed": true } ],
  "totalObtained": "…", "totalMax": 400, "percentage": 84.20, "grade": "A2", "gradePoint": 9.0, "result": "PASS",
  "sectionRank": 2, "sectionSize": 3,
  "coScholastic": [ { "area": "ART_EDUCATION", "grade": "A" } ],
  "teacherRemarks": "Aarav has settled in well this year…", "published": true } }
```

{expand}

{expand:title=US-05010: Publish digital report cards directly to parent mobile application portals}

`POST /api/v1/exams/terms/{termId}/publish?branchId=` with `{ "sectionId" }` — CLASS_TEACHER, SCHOOL_PRINCIPAL, EXAM_CONTROLLER. Refused (`409`) while any entry of the section is missing; otherwise every student's card is published (`published`, `alreadyPublished` counts). Parents then see the card and the term in their child's trend. **Audit**: `PROGRESS_CARDS_PUBLISHED`.

{expand}

{expand:title=US-05011: Generate class rank lists, subject topper reports, and percentile bands}

`GET /api/v1/exams/terms/{termId}/rank-list?gradeId=|sectionId=&branchId=` — give exactly one of `gradeId` (all sections) or `sectionId` (`400` otherwise). Returns `ranks[]` (`rank`, student, `className`, `percentage`, `grade`, `percentileBand`) and `subjectToppers[]` (`topMarks`, `students[]` — ties included).

{expand}

{expand:title=US-05013: Track term-on-term academic progress trends using comparative bar charts}

`GET /api/v1/exams/students/{studentId}/progress-trend` — teaching staff, registrar, parent (published terms only). Returns every term with marks, oldest first: `percentage`, `grade`, `result`, `sectionRank`, `changeFromPrevious`, `published`, `subjects[]` (per-subject % and grade, used for the comparative bars), plus `weightedPercentage`, `weightedGrade` and `trend`. Feeds the student profile "Academic progress" card.

{expand}

{expand:title=US-05016: Flag incomplete grade entries before initiating final report card compilation}

`GET /api/v1/exams/terms/{termId}/completeness?sectionId=|gradeId=&branchId=` — teaching staff, registrar. Without filters, every section of every class with subjects in the term. Returns `complete`, `expectedEntries`, `enteredEntries`, `missingEntries` and `items[]` (class, subject, expected, entered, `missing[]` students); only sections with gaps are listed unless one section is asked for.

{expand}

{expand:title=US-05018: Attach custom teacher narrative remarks to individual student progress cards}

`PUT /api/v1/exams/students/{studentId}/progress-card/remarks` with `{ "examTermId", "remarks" (≤ 1000) }` — CLASS_TEACHER, SCHOOL_PRINCIPAL. `409` once the card is published. Returns the progress card. **Audit**: `PROGRESS_REMARKS_SAVED`.

{expand}

---

## 4. Seeded data (DPS, 2026-2027)

| What | Detail |
|---|---|
| Accounts | `examcontroller_rajiv` (EXAM_CONTROLLER), `subjectteacher_neha` (SUBJECT_TEACHER), `academicauditor_vikram` (ACADEMIC_AUDITOR); `classteacher_priya`, `viceprincipal_kiran`, `registrar_suresh`, `parent_rohit` from earlier seeds (password `Password@123`) |
| Scale | CBSE 9-point: A1 91, A2 81, B1 71, B2 61, C1 51, C2 41, D 33, E 0; pass 33% |
| Terms | Periodic Test 1 (formative 20%, locked by hand), Half-Yearly (summative 30%, closed by T_lock 10 Oct), Periodic Test 2 (formative 20%, open), Annual (summative 30%, open) |
| Subjects | Class 1-5 ENG HIN MATH EVS; Class 6-10 ENG HIN MATH SCI (×1.5) SST; Class 11-12 ENG PHY CHEM MATH CS; tests out of 40, others out of 80 |
| Marks | Periodic Test 1 and Half-Yearly for every active student; Om Varma fails Half-Yearly Maths; Diya Nair absent for Half-Yearly English; Riya Bansal's Half-Yearly Science missing |
| Workflow | Pending unlock ticket for Class 7-A Science; co-scholastic grades for 6-A and 7-A; Periodic Test 1 published for all, Half-Yearly for 6-A with teacher remarks |
