package com.kvs.exam.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kvs.core.service.JwtTokenService;
import com.kvs.exam.entity.AcademicSession;
import com.kvs.exam.entity.Grade;
import com.kvs.exam.entity.Section;
import com.kvs.exam.entity.StudentMaster;
import com.kvs.exam.enums.Gender;
import com.kvs.exam.enums.StudentStatus;
import com.kvs.exam.repo.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Module 05 assessments in studentservice (US-05000 … US-05018): setup, mark entry, T_lock and unlock tickets,
 * broadsheet, ranks, co-scholastic grades, publishing, the progress card and the progress trend.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExamControllerIntegrationTest {

    private static final String TENANT = "TENANT-EXAM";
    private static final String PARENT = "parent-exam-1";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private AcademicSessionRepository sessionRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private StudentMasterRepository studentRepository;
    @Autowired private GradingScaleRepository scaleRepository;
    @Autowired private ExamTermRepository termRepository;
    @Autowired private ExamSubjectRepository subjectRepository;
    @Autowired private MarkEntryRepository markRepository;
    @Autowired private MarkUnlockTicketRepository ticketRepository;
    @Autowired private CoScholasticGradeRepository coScholasticRepository;
    @Autowired private ProgressCardRepository cardRepository;

    private AcademicSession session;
    private Grade grade;
    private Section section;
    private StudentMaster asha;
    private StudentMaster bilal;
    private StudentMaster chitra;
    private String scaleId;

    @BeforeEach
    void setUp() throws Exception {
        int year = LocalDate.now().getMonthValue() >= 4 ? LocalDate.now().getYear() : LocalDate.now().getYear() - 1;
        session = sessionRepository.save(AcademicSession.builder().tenantId(TENANT).sessionName(year + "-" + (year + 1))
                .startDate(LocalDate.of(year, 4, 1)).endDate(LocalDate.of(year + 1, 3, 31)).isActive(true).build());
        grade = gradeRepository.save(Grade.builder().tenantId(TENANT).gradeName("Class 6").gradeCode("GRADE-06-T").sequenceOrder(9).build());
        section = sectionRepository.save(Section.builder().gradeId(grade.getGradeId()).sectionName("A").build());
        asha = student("Asha", 1, PARENT);
        bilal = student("Bilal", 2, null);
        chitra = student("Chitra", 3, null);
        scaleId = data(post("/api/v1/exams/grading-scales", token("EXAM_CONTROLLER"), Map.of("scaleName", "CBSE 9-point", "passPercent", 33,
                "bands", List.of(band("A1", 91, 10), band("A2", 81, 9), band("B1", 71, 8), band("B2", 61, 7), band("C1", 51, 6),
                        band("C2", 41, 5), band("D", 33, 4), band("E", 0, 0))))
                .andExpect(status().isCreated())).path("gradingScaleId").asText();
    }

    @AfterEach
    void tearDown() {
        cardRepository.deleteAll();
        coScholasticRepository.deleteAll();
        ticketRepository.deleteAll();
        markRepository.deleteAll();
        subjectRepository.deleteAll();
        termRepository.deleteAll();
        scaleRepository.deleteAll();
        studentRepository.deleteAll(List.of(asha, bilal, chitra));
        sectionRepository.delete(section);
        gradeRepository.delete(grade);
        sessionRepository.delete(session);
    }

    // ------------------------------------------------------------------ helpers

    private StudentMaster student(String first, int roll, String parent) {
        return studentRepository.save(StudentMaster.builder().tenantId(TENANT).branchId("BR-1")
                .studentUid("EX-" + first.toUpperCase() + "-" + UUID.randomUUID().toString().substring(0, 6))
                .firstName(first).lastName("Test").dateOfBirth(LocalDate.of(2014, 5, 1)).gender(Gender.FEMALE)
                .gradeId(grade.getGradeId()).sectionId(section.getSectionId()).rollNumber(roll).parentAccountId(parent)
                .studentStatus(StudentStatus.ACTIVE).build());
    }

    private static Map<String, Object> band(String label, int min, int point) {
        return Map.of("gradeLabel", label, "minPercent", min, "gradePoint", point);
    }

    private String token(String role) {
        return token(role, TENANT, role.toLowerCase() + "-user");
    }

    private String token(String role, String tenant, String userId) {
        return "Bearer " + jwtTokenService.generateToken(role.toLowerCase() + "@kvs.local",
                Map.of("roles", List.of("ROLE_" + role), "tenantId", tenant, "userId", userId));
    }

    private ResultActions post(String path, String token, Object body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions putJson(String path, String token, Object body) throws Exception {
        return mockMvc.perform(put(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions getAs(String path, String token, String... params) throws Exception {
        var req = get(path).header("Authorization", token);
        for (int i = 0; i < params.length; i += 2) {
            req = req.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(req);
    }

    private JsonNode data(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString()).path("data");
    }

    /** A term (deadline in 10 days unless given) with English (max 80, pass 27) and Maths (max 80, pass 27, weight 2). */
    private String term(String name, int weight, Instant deadline) throws Exception {
        String termId = data(post("/api/v1/exams/terms", token("EXAM_CONTROLLER"), Map.of(
                "academicYearId", session.getAcademicYearId(), "termName", name, "termType", "SUMMATIVE", "weightagePercent", weight,
                "startDate", session.getStartDate().plusDays(10).toString(), "endDate", session.getStartDate().plusDays(20).toString(),
                "markEntryDeadline", (deadline == null ? Instant.now().plus(10, ChronoUnit.DAYS) : deadline).toString(),
                "gradingScaleId", scaleId)).andExpect(status().isCreated())).path("examTermId").asText();
        post("/api/v1/exams/terms/" + termId + "/subjects", token("EXAM_CONTROLLER"), Map.of("gradeId", grade.getGradeId(),
                "subjects", List.of(
                        Map.of("subjectCode", "ENG", "subjectName", "English", "maxMarks", 80, "passMarks", 27),
                        Map.of("subjectCode", "MATH", "subjectName", "Mathematics", "maxMarks", 80, "passMarks", 27, "weightage", 2))))
                .andExpect(status().isCreated());
        return termId;
    }

    private ResultActions marks(String termId, String subject, String role, Object... studentMarks) throws Exception {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (int i = 0; i < studentMarks.length; i += 2) {
            Map<String, Object> e = new HashMap<>();
            e.put("studentId", ((StudentMaster) studentMarks[i]).getStudentId());
            if (studentMarks[i + 1] == null) {
                e.put("absent", true);
            } else {
                e.put("marksObtained", studentMarks[i + 1]);
            }
            entries.add(e);
        }
        return putJson("/api/v1/exams/terms/" + termId + "/marks", token(role),
                Map.of("sectionId", section.getSectionId(), "subjectCode", subject, "entries", entries));
    }

    private void fullMarks(String termId) throws Exception {
        marks(termId, "ENG", "SUBJECT_TEACHER", asha, 72, bilal, 40, chitra, 20).andExpect(status().isOk());
        marks(termId, "MATH", "SUBJECT_TEACHER", asha, 76, bilal, 40, chitra, 60).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ US-05000

    @Test
    @DisplayName("US-05000: grading scale validation 400, duplicate 409, subject teacher 403, no token 401")
    void scaleNegatives() throws Exception {
        post("/api/v1/exams/grading-scales", token("EXAM_CONTROLLER"), Map.of("scaleName", "", "bands", List.of()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        post("/api/v1/exams/grading-scales", token("EXAM_CONTROLLER"), Map.of("scaleName", "No zero", "passPercent", 33,
                "bands", List.of(band("A", 50, 5)))).andExpect(status().isBadRequest());
        post("/api/v1/exams/grading-scales", token("SCHOOL_PRINCIPAL"), Map.of("scaleName", "cbse 9-point", "passPercent", 33,
                "bands", List.of(band("A", 0, 5)))).andExpect(status().isConflict());
        post("/api/v1/exams/grading-scales", token("SUBJECT_TEACHER"), Map.of("scaleName", "X", "passPercent", 33,
                "bands", List.of(band("A", 0, 5)))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/exams/grading-scales")).andExpect(status().isUnauthorized());
        getAs("/api/v1/exams/grading-scales", token("ACADEMIC_AUDITOR")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].bands[0].gradeLabel").value("A1"));
    }

    @Test
    @DisplayName("US-05000: exam controller creates a term with subjects (201, trace id); weightage over 100% 400; outside the year 400; duplicate 409")
    void termsAndSubjects() throws Exception {
        String termId = term("Half-Yearly", 60, null);
        mockMvc.perform(get("/api/v1/exams/terms/{id}", termId).header("Authorization", token("CLASS_TEACHER"))
                        .header("X-Trace-Id", "trace-exam-001"))
                .andExpect(status().isOk()).andExpect(header().string("X-Trace-Id", "trace-exam-001"))
                .andExpect(jsonPath("$.data.locked").value(false)).andExpect(jsonPath("$.data.subjectCount").value(2))
                .andExpect(jsonPath("$.data.gradingScaleName").value("CBSE 9-point"));
        Map<String, Object> base = new HashMap<>(Map.of("academicYearId", session.getAcademicYearId(), "termName", "Annual", "termType", "SUMMATIVE",
                "weightagePercent", 50, "startDate", session.getStartDate().plusDays(100).toString(),
                "endDate", session.getStartDate().plusDays(110).toString(), "markEntryDeadline", Instant.now().plus(5, ChronoUnit.DAYS).toString(),
                "gradingScaleId", scaleId));
        post("/api/v1/exams/terms", token("EXAM_CONTROLLER"), base).andExpect(status().isBadRequest());
        base.put("weightagePercent", 40);
        base.put("endDate", session.getEndDate().plusDays(5).toString());
        post("/api/v1/exams/terms", token("EXAM_CONTROLLER"), base).andExpect(status().isBadRequest());
        base.put("endDate", session.getStartDate().plusDays(110).toString());
        base.put("termName", "half-yearly");
        post("/api/v1/exams/terms", token("EXAM_CONTROLLER"), base).andExpect(status().isConflict());
        post("/api/v1/exams/terms", token("REGISTRAR"), base).andExpect(status().isForbidden());

        post("/api/v1/exams/terms/" + termId + "/subjects", token("EXAM_CONTROLLER"), Map.of("gradeId", grade.getGradeId(),
                "subjects", List.of(Map.of("subjectCode", "SCI", "subjectName", "Science", "maxMarks", 50, "passMarks", 60))))
                .andExpect(status().isBadRequest());
        post("/api/v1/exams/terms/" + termId + "/subjects", token("EXAM_CONTROLLER"), Map.of("gradeId", UUID.randomUUID(),
                "subjects", List.of(Map.of("subjectCode", "SCI", "subjectName", "Science", "maxMarks", 50, "passMarks", 17))))
                .andExpect(status().isNotFound());
        getAs("/api/v1/exams/terms/" + termId, token("EXAM_CONTROLLER", "TENANT-OTHER", "u")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ US-05003 / US-05005 / US-05006

    @Test
    @DisplayName("US-05003: subject teacher fills the mark grid; above max, both marks and absent, and other students 400; principal 403")
    void markEntry() throws Exception {
        String termId = term("Half-Yearly", 50, null);
        marks(termId, "ENG", "SUBJECT_TEACHER", asha, 72, bilal, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enteredCount").value(2)).andExpect(jsonPath("$.data.missingCount").value(1))
                .andExpect(jsonPath("$.data.rows[1].absent").value(true))
                .andExpect(jsonPath("$.data.rows[0].evaluatedBy").value("subject_teacher@kvs.local"));
        marks(termId, "ENG", "SUBJECT_TEACHER", asha, 81).andExpect(status().isBadRequest());
        putJson("/api/v1/exams/terms/" + termId + "/marks", token("CLASS_TEACHER"), Map.of("sectionId", section.getSectionId(),
                "subjectCode", "ENG", "entries", List.of(Map.of("studentId", asha.getStudentId(), "marksObtained", 50, "absent", true))))
                .andExpect(status().isBadRequest());
        putJson("/api/v1/exams/terms/" + termId + "/marks", token("CLASS_TEACHER"), Map.of("sectionId", section.getSectionId(),
                "subjectCode", "ENG", "entries", List.of(Map.of("studentId", UUID.randomUUID(), "marksObtained", 50))))
                .andExpect(status().isBadRequest());
        marks(termId, "ENG", "SCHOOL_PRINCIPAL", asha, 50).andExpect(status().isForbidden());
        marks(termId, "HINDI", "SUBJECT_TEACHER", asha, 50).andExpect(status().isNotFound());
        getAs("/api/v1/exams/terms/" + termId + "/marks", token("ACADEMIC_AUDITOR")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("US-05005 / US-05006: T_lock blocks marks (409); a ticket approved by the principal reopens the subject; own ticket and rejection rules")
    void lockAndUnlock() throws Exception {
        String termId = term("Half-Yearly", 50, Instant.now().minus(1, ChronoUnit.HOURS));
        getAs("/api/v1/exams/terms/" + termId + "/marks", token("SUBJECT_TEACHER"), "sectionId", section.getSectionId().toString(), "subjectCode", "ENG")
                .andExpect(jsonPath("$.data.locked").value(true))
                .andExpect(jsonPath("$.data.lockReason").value("Mark entry closed at the deadline (T_lock)"));
        marks(termId, "ENG", "SUBJECT_TEACHER", asha, 50).andExpect(status().isConflict());
        post("/api/v1/exams/terms/" + termId + "/lock", token("EXAM_CONTROLLER"), Map.of()).andExpect(status().isConflict());

        Map<String, Object> ticket = Map.of("sectionId", section.getSectionId(), "subjectCode", "ENG", "reason", "Asha's paper was re-evaluated");
        String ticketId = data(post("/api/v1/exams/terms/" + termId + "/unlock-tickets", token("SUBJECT_TEACHER"), ticket)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PENDING"))).path("ticketId").asText();
        post("/api/v1/exams/terms/" + termId + "/unlock-tickets", token("CLASS_TEACHER"), ticket).andExpect(status().isConflict());
        post("/api/v1/exams/terms/" + termId + "/unlock-tickets", token("ACADEMIC_AUDITOR"), ticket).andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/exams/unlock-tickets/{id}/decision", ticketId).header("Authorization", token("SUBJECT_TEACHER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/exams/unlock-tickets/{id}/decision", ticketId).header("Authorization", token("SCHOOL_PRINCIPAL"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECTED\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/exams/unlock-tickets/{id}/decision", ticketId).header("Authorization", token("SCHOOL_PRINCIPAL"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\",\"unlockHours\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.decidedBy").value("school_principal@kvs.local"));
        marks(termId, "ENG", "SUBJECT_TEACHER", asha, 50).andExpect(status().isOk()).andExpect(jsonPath("$.data.locked").value(false));
        marks(termId, "MATH", "SUBJECT_TEACHER", asha, 50).andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/exams/unlock-tickets/{id}/decision", ticketId).header("Authorization", token("EXAM_CONTROLLER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}")).andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/exams/unlock-tickets/{id}/decision", UUID.randomUUID()).header("Authorization", token("EXAM_CONTROLLER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}")).andExpect(status().isNotFound());
        getAs("/api/v1/exams/terms/" + termId + "/unlock-tickets", token("SCHOOL_PRINCIPAL"))
                .andExpect(jsonPath("$.data[0].subjectName").value("English")).andExpect(jsonPath("$.data[0].className").value("Class 6-A"));
    }

    @Test
    @DisplayName("US-05005: the exam controller locks a term early; marks are refused and a teacher cannot lock (403)")
    void manualLock() throws Exception {
        String termId = term("Half-Yearly", 50, null);
        marks(termId, "ENG", "SUBJECT_TEACHER", asha, 60).andExpect(status().isOk());
        post("/api/v1/exams/terms/" + termId + "/lock", token("SUBJECT_TEACHER"), Map.of()).andExpect(status().isForbidden());
        post("/api/v1/exams/terms/" + termId + "/lock", token("EXAM_CONTROLLER"), Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.locked").value(true));
        marks(termId, "ENG", "SUBJECT_TEACHER", asha, 61).andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ results

    @Test
    @DisplayName("US-05016 / US-05010: missing entries are flagged and block publishing (409); complete marks publish")
    void completenessAndPublish() throws Exception {
        String termId = term("Half-Yearly", 50, null);
        marks(termId, "ENG", "SUBJECT_TEACHER", asha, 72, bilal, 40).andExpect(status().isOk());
        getAs("/api/v1/exams/terms/" + termId + "/completeness", token("EXAM_CONTROLLER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.complete").value(false))
                .andExpect(jsonPath("$.data.expectedEntries").value(6)).andExpect(jsonPath("$.data.missingEntries").value(4))
                .andExpect(jsonPath("$.data.items[0].missing[0].studentName").value("Chitra Test"));
        post("/api/v1/exams/terms/" + termId + "/publish", token("CLASS_TEACHER"), Map.of("sectionId", section.getSectionId()))
                .andExpect(status().isConflict());
        marks(termId, "ENG", "SUBJECT_TEACHER", chitra, 20).andExpect(status().isOk());
        marks(termId, "MATH", "SUBJECT_TEACHER", asha, 76, bilal, 40, chitra, 60).andExpect(status().isOk());
        post("/api/v1/exams/terms/" + termId + "/publish", token("SUBJECT_TEACHER"), Map.of("sectionId", section.getSectionId()))
                .andExpect(status().isForbidden());
        post("/api/v1/exams/terms/" + termId + "/publish", token("CLASS_TEACHER"), Map.of("sectionId", section.getSectionId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.published").value(3));
        post("/api/v1/exams/terms/" + termId + "/publish", token("SCHOOL_PRINCIPAL"), Map.of("sectionId", section.getSectionId()))
                .andExpect(jsonPath("$.data.published").value(0)).andExpect(jsonPath("$.data.alreadyPublished").value(3));
    }

    @Test
    @DisplayName("US-05007 / US-05011: broadsheet with weighted percentages, grades, results and ranks; rank list with toppers and bands")
    void broadsheetAndRanks() throws Exception {
        String termId = term("Half-Yearly", 50, null);
        fullMarks(termId);
        // Asha: ENG 90%, MATH 95% (weight 2) → 93.33% A1; Chitra: ENG 25% (fail), MATH 75% → 58.33% C1 FAIL; Bilal 50% C2 PASS
        getAs("/api/v1/exams/terms/" + termId + "/broadsheet", token("ACADEMIC_AUDITOR"), "sectionId", section.getSectionId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.className").value("Class 6-A"))
                .andExpect(jsonPath("$.data.rows[0].percentage").value(93.33))
                .andExpect(jsonPath("$.data.rows[0].grade").value("A1"))
                .andExpect(jsonPath("$.data.rows[0].rank").value(1))
                .andExpect(jsonPath("$.data.rows[2].result").value("FAIL"))
                .andExpect(jsonPath("$.data.rows[2].rank").value(2))
                .andExpect(jsonPath("$.data.rows[1].grade").value("C2"))
                .andExpect(jsonPath("$.data.subjects[0].failed").value(1));
        getAs("/api/v1/exams/terms/" + termId + "/rank-list", token("SCHOOL_PRINCIPAL"), "gradeId", grade.getGradeId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ranks[0].studentName").value("Asha Test"))
                .andExpect(jsonPath("$.data.ranks[0].percentileBand").value("Top 50%"))
                .andExpect(jsonPath("$.data.subjectToppers[1].students[0]").value("Asha Test"))
                .andExpect(jsonPath("$.data.subjectToppers[1].topMarks").value(76.0));
        getAs("/api/v1/exams/terms/" + termId + "/rank-list", token("SCHOOL_PRINCIPAL")).andExpect(status().isBadRequest());
        getAs("/api/v1/exams/terms/" + termId + "/rank-list", token("APPLICANT_PARENT"), "gradeId", grade.getGradeId().toString())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("US-05008 / US-05009 / US-05018: co-scholastic grades and remarks appear on the progress card; remarks locked after publishing")
    void progressCard() throws Exception {
        String termId = term("Half-Yearly", 50, null);
        fullMarks(termId);
        putJson("/api/v1/exams/terms/" + termId + "/co-scholastic", token("CLASS_TEACHER"), Map.of("entries", List.of(
                Map.of("studentId", asha.getStudentId(), "area", "ART_EDUCATION", "grade", "A+"),
                Map.of("studentId", asha.getStudentId(), "area", "HEALTH_AND_PHYSICAL_EDUCATION", "grade", "A", "remarks", "House football captain"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[0].grades.length()").value(2));
        putJson("/api/v1/exams/terms/" + termId + "/co-scholastic", token("CLASS_TEACHER"), Map.of("entries", List.of(
                Map.of("studentId", asha.getStudentId(), "area", "ART_EDUCATION", "grade", "B+")))).andExpect(status().isBadRequest());
        putJson("/api/v1/exams/terms/" + termId + "/co-scholastic", token("REGISTRAR"), Map.of("entries", List.of(
                Map.of("studentId", asha.getStudentId(), "area", "ART_EDUCATION", "grade", "A")))).andExpect(status().isForbidden());

        putJson("/api/v1/exams/students/" + asha.getStudentId() + "/progress-card/remarks", token("CLASS_TEACHER"),
                Map.of("examTermId", termId, "remarks", "Consistent and curious; reads widely."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.teacherRemarks").value("Consistent and curious; reads widely."));
        getAs("/api/v1/exams/students/" + asha.getStudentId() + "/progress-card", token("REGISTRAR"), "examTermId", termId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.percentage").value(93.33)).andExpect(jsonPath("$.data.grade").value("A1"))
                .andExpect(jsonPath("$.data.gradePoint").value(10.0)).andExpect(jsonPath("$.data.result").value("PASS"))
                .andExpect(jsonPath("$.data.sectionRank").value(1)).andExpect(jsonPath("$.data.sectionSize").value(3))
                .andExpect(jsonPath("$.data.coScholastic[0].grade").value("A+"))
                .andExpect(jsonPath("$.data.published").value(false));

        String parent = token("APPLICANT_PARENT", TENANT, PARENT);
        getAs("/api/v1/exams/students/" + asha.getStudentId() + "/progress-card", parent, "examTermId", termId).andExpect(status().isNotFound());
        post("/api/v1/exams/terms/" + termId + "/publish", token("CLASS_TEACHER"), Map.of("sectionId", section.getSectionId())).andExpect(status().isOk());
        getAs("/api/v1/exams/students/" + asha.getStudentId() + "/progress-card", parent, "examTermId", termId)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.published").value(true));
        getAs("/api/v1/exams/students/" + bilal.getStudentId() + "/progress-card", parent, "examTermId", termId).andExpect(status().isNotFound());
        putJson("/api/v1/exams/students/" + asha.getStudentId() + "/progress-card/remarks", token("CLASS_TEACHER"),
                Map.of("examTermId", termId, "remarks", "Changed")).andExpect(status().isConflict());
        putJson("/api/v1/exams/students/" + asha.getStudentId() + "/progress-card/remarks", token("SUBJECT_TEACHER"),
                Map.of("examTermId", termId, "remarks", "x")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("US-05013: progress trend across terms with change, weighted percentage and trend; parents only see published terms")
    void progressTrend() throws Exception {
        String t1 = term("Periodic Test 1", 20, null);
        marks(t1, "ENG", "SUBJECT_TEACHER", asha, 56, bilal, 40, chitra, 40).andExpect(status().isOk());
        marks(t1, "MATH", "SUBJECT_TEACHER", asha, 56, bilal, 40, chitra, 40).andExpect(status().isOk());
        String t2 = data(post("/api/v1/exams/terms", token("EXAM_CONTROLLER"), Map.of(
                "academicYearId", session.getAcademicYearId(), "termName", "Half-Yearly", "termType", "SUMMATIVE", "weightagePercent", 30,
                "startDate", session.getStartDate().plusDays(150).toString(), "endDate", session.getStartDate().plusDays(160).toString(),
                "markEntryDeadline", Instant.now().plus(10, ChronoUnit.DAYS).toString(), "gradingScaleId", scaleId))).path("examTermId").asText();
        post("/api/v1/exams/terms/" + t2 + "/subjects", token("EXAM_CONTROLLER"), Map.of("gradeId", grade.getGradeId(),
                "subjects", List.of(Map.of("subjectCode", "ENG", "subjectName", "English", "maxMarks", 80, "passMarks", 27),
                        Map.of("subjectCode", "MATH", "subjectName", "Mathematics", "maxMarks", 80, "passMarks", 27)))).andExpect(status().isCreated());
        marks(t2, "ENG", "SUBJECT_TEACHER", asha, 72, bilal, 40, chitra, 40).andExpect(status().isOk());
        marks(t2, "MATH", "SUBJECT_TEACHER", asha, 72, bilal, 40, chitra, 40).andExpect(status().isOk());
        // Asha: 70% then 90% → +20, weighted (70×20 + 90×30) / 50 = 82
        getAs("/api/v1/exams/students/" + asha.getStudentId() + "/progress-trend", token("CLASS_TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.terms.length()").value(2))
                .andExpect(jsonPath("$.data.terms[0].percentage").value(70.0))
                .andExpect(jsonPath("$.data.terms[1].changeFromPrevious").value(20.0))
                .andExpect(jsonPath("$.data.terms[1].sectionRank").value(1))
                .andExpect(jsonPath("$.data.weightedPercentage").value(82.0))
                .andExpect(jsonPath("$.data.weightedGrade").value("A2"))
                .andExpect(jsonPath("$.data.trend").value("IMPROVING"));
        String parent = token("APPLICANT_PARENT", TENANT, PARENT);
        post("/api/v1/exams/terms/" + t1 + "/publish", token("CLASS_TEACHER"), Map.of("sectionId", section.getSectionId())).andExpect(status().isOk());
        getAs("/api/v1/exams/students/" + asha.getStudentId() + "/progress-trend", parent)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.terms.length()").value(1))
                .andExpect(jsonPath("$.data.trend").value("NOT_ENOUGH_DATA"));
        getAs("/api/v1/exams/students/" + bilal.getStudentId() + "/progress-trend", parent).andExpect(status().isNotFound());
        getAs("/api/v1/exams/students/" + asha.getStudentId() + "/progress-trend", token("CLASS_TEACHER", "TENANT-OTHER", "u"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("US-03005 / US-05009: after a promotion the term's results keep the class they were earned in; the promoted student is on the new class roster")
    void resultsSurvivePromotion() throws Exception {
        String termId = term("Annual", 30, null);
        fullMarks(termId);
        String next = term("Next year test", 20, null);
        Grade grade7 = gradeRepository.save(Grade.builder().tenantId(TENANT).gradeName("Class 7").gradeCode("GRADE-07-T").sequenceOrder(10).build());
        Section section7 = sectionRepository.save(Section.builder().gradeId(grade7.getGradeId()).sectionName("A").build());
        try {
            // Batch promotion (studentservice) moves Asha to Class 7-A with status PROMOTED
            asha.setGradeId(grade7.getGradeId());
            asha.setSectionId(section7.getSectionId());
            asha.setStudentStatus(StudentStatus.PROMOTED);
            asha = studentRepository.save(asha);

            getAs("/api/v1/exams/students/" + asha.getStudentId() + "/progress-card", token("REGISTRAR"), "examTermId", termId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.className").value("Class 6-A"))
                    .andExpect(jsonPath("$.data.sectionRank").value(1)).andExpect(jsonPath("$.data.sectionSize").value(3));
            getAs("/api/v1/exams/students/" + asha.getStudentId() + "/progress-trend", token("CLASS_TEACHER"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.terms[0].className").value("Class 6-A"))
                    .andExpect(jsonPath("$.data.className").value("Class 7-A"));
            getAs("/api/v1/exams/terms/" + termId + "/broadsheet", token("CLASS_TEACHER"), "sectionId", section.getSectionId().toString())
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows.length()").value(3));
            // A promoted student is on roll in the new section for terms without marks yet
            post("/api/v1/exams/terms/" + next + "/subjects", token("EXAM_CONTROLLER"), Map.of("gradeId", grade7.getGradeId(),
                    "subjects", List.of(Map.of("subjectCode", "ENG", "subjectName", "English", "maxMarks", 80, "passMarks", 27))))
                    .andExpect(status().isCreated());
            getAs("/api/v1/exams/terms/" + next + "/marks", token("SUBJECT_TEACHER"), "sectionId", section7.getSectionId().toString(), "subjectCode", "ENG")
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows.length()").value(1))
                    .andExpect(jsonPath("$.data.rows[0].studentId").value(asha.getStudentId().toString()));
        } finally {
            subjectRepository.deleteAll();
            sectionRepository.delete(section7);
            gradeRepository.delete(grade7);
        }
    }
}
