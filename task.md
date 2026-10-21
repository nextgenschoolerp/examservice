# Task Execution Record: Module 05 — Examinations, Grading & NEP 2020 Assessments (first release)

Execution of the `BRD_API_Generate` skill for **Module 05** based on [`story/module5/School_ERP_Module_05_Exams_Assessment_1000.md`](../story/module5/School_ERP_Module_05_Exams_Assessment_1000.md), [`module5.sql`](../story/module5/module5.sql) and [`module5.md`](../story/module5/module5.md).

---

## 1. Input Specification & Target Microservice Resolution

- **User Stories**: `US-05000`, `US-05003`, `US-05005`, `US-05006`, `US-05007`, `US-05008`, `US-05009`, `US-05010`, `US-05011`, `US-05013`, `US-05016`, `US-05018` (scenario 1 of each; the BRD's 1,000 entries are 50 variations of 20 base stories)
- **Target Microservice**: `examservice`, base package `com.kvs.exam`
- **Port**: `8086`, context path `/examservice`, test port `18086`
- **Database**: shared PostgreSQL database `authservice`, Hibernate `ddl-auto: update`
- **Service Lifecycle Status**:
  - [ ] Existing Microservice
  - [x] New Microservice — first built inside studentservice at the product owner's request, then moved here on their request (18 Oct 2026)
- **Read model**: `StudentMaster`, `Grade`, `Section`, `AcademicSession` map studentservice's tables read-only for rosters and class names

---

## 2. Requirement Extraction (from BRD)

| Story | Actor (scenario 1) → role | Action |
|---|---|---|
| US-05000 | Exam Controller → `EXAM_CONTROLLER` | Configure assessment terms, subject weightage ratios, and grading scales |
| US-05003 | Academic Principal (BRD) / Subject Teacher → `SUBJECT_TEACHER` | Enter student scholastic scores via web mark entry grids |
| US-05005 | Subject Teacher / Exam Controller | Lock score entry automatically upon timer expiration (T_lock) |
| US-05006 | Student (BRD) / Subject Teacher → ticket; Academic Principal → `SCHOOL_PRINCIPAL` decides | Ticket requests to unlock locked mark sheets |
| US-05007 | Academic Auditor → `ACADEMIC_AUDITOR` | Compile master broadsheets |
| US-05008 | Exam Controller, Class Teacher → `CLASS_TEACHER` | Evaluate co-scholastic rubrics |
| US-05009 | Subject Teacher | Synthesize NEP 2020 holistic progress cards |
| US-05010 | Class Teacher | Publish report cards to parents |
| US-05011 | Academic Principal | Rank lists, subject toppers and percentile bands |
| US-05013 | Parent → `APPLICANT_PARENT` | Term-on-term progress trends (comparative bars) |
| US-05016 | Exam Controller | Flag incomplete grade entries |
| US-05018 | Class Teacher | Teacher narrative remarks on progress cards |

The BRD rotates the eight actors across scenarios; roles were assigned by who does the work in a school (Evaluator shares `SUBJECT_TEACHER`; students have no login yet).

### Acceptance Criteria (each story)
- [x] **AC-1 Privileges**: [`ExamRoles`](src/main/java/com/kvs/exam/config/ExamRoles.java); parents only their own children's published results
- [x] **AC-2 Execution**: endpoints accept the documented payloads
- [x] **AC-3 Validation**: `400 VALIDATION_FAILED` / `BAD_REQUEST`
- [x] **AC-4 Persistence**: transactional services
- [x] **AC-5 Audit trail**: `GRADING_SCALE_CREATED`, `EXAM_TERM_CREATED`, `EXAM_SUBJECTS_CONFIGURED`, `EXAM_TERM_LOCKED`, `MARKS_ENTERED`, `MARK_UNLOCK_*`, `CO_SCHOLASTIC_GRADED`, `PROGRESS_REMARKS_SAVED`, `PROGRESS_CARDS_PUBLISHED`
- [x] **AC-6 Security**: `401`, `403`, `404` for another institution

---

## 3. API Contract

See [`wiki/api-docs/School_ERP_Module_05_Exams_API_Documentation.md`](wiki/api-docs/School_ERP_Module_05_Exams_API_Documentation.md). All endpoints are under `/api/v1/exams`: `grading-scales`, `terms`, `terms/{id}/subjects`, `terms/{id}/lock`, `terms/{id}/marks`, `terms/{id}/unlock-tickets`, `unlock-tickets/{id}/decision`, `terms/{id}/completeness`, `terms/{id}/broadsheet`, `terms/{id}/rank-list`, `terms/{id}/co-scholastic`, `terms/{id}/publish`, `students/{id}/progress-card`, `students/{id}/progress-card/remarks`, `students/{id}/progress-trend`.

### Components
- **Entities**: `GradingScale` (+ `GradeBand`), `ExamTerm`, `ExamSubject`, `MarkEntry`, `MarkUnlockTicket`, `CoScholasticGrade`, `ProgressCard`, `AuditLog`; read model `StudentMaster`, `Grade`, `Section`, `AcademicSession`
- **Services**: `ExamSetupService`, `MarkEntryService`, `ExamResultService`, `ExamAccess`, `TenantAccessGuard`, `StudentAuditService`
- **Controller**: `ExamController`
- **Tests**: `ExamControllerIntegrationTest`

---

## 4. Implementation Checklist

- [x] Step 0 scaffolding (build.gradle, settings, wrapper, application.yml with port / context path, bootstrap, security, JPA auditing) — compiled
- [x] Steps 1–5 entities, DTOs, repositories, services, controller
- [x] Step 6 integration tests: 201/200, 400, 401, 403, 404, 409, trace header — **9 tests, 0 failures**
- [x] Step 7 Postman [`postman/School_ERP_ExamService.postman_collection.json`](postman/School_ERP_ExamService.postman_collection.json): sign-in folder + one folder per story (49 requests), variables validated
- [x] Step 8 wiki with `{expand}` per story
- [x] Seed [`module05_exam_seed.sql`](../studentservice/src/main/resources/db/seed/module05_exam_seed.sql) (kept with the Module 03 seed it builds on, in studentservice) (roles, 3 accounts, scale, 4 terms, 220 subjects, 481 marks, ticket, co-scholastic grades, published cards)
- [x] UI: Vite proxy `/api/v1/exams` → `http://localhost:8086/examservice` (`EXAM_SERVICE_URL`); student profile Academic progress card; Academics page assessments workspace — see [`frontend/docs/examservice-api-mapping.md`](../frontend/docs/examservice-api-mapping.md)
- [x] UI regression cases `scripts/cases/examservice.json` and cleanup

## 5. Verification

```bash
cd examservice
.\gradlew.bat test
```

- [x] 9 tests, 0 failures (studentservice back to its 120 tests after the move)
- [x] Seed checked through the API; browser: mark entry, broadsheet, profile progress card on the test stack

## 6. Not yet implemented

US-05001 seating, US-05002 admit cards, US-05004 Excel import, US-05012 self / peer appraisal, US-05014 grace marks, US-05015 LOC XML export, US-05017 failure analysis, US-05019 archiving.
