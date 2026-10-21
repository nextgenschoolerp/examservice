package com.kvs.exam.controller;

import com.kvs.core.dto.ApiResponse;
import com.kvs.core.exception.BadRequestException;
import com.kvs.exam.config.ExamRoles;
import com.kvs.exam.dto.*;
import com.kvs.exam.service.ExamResultService;
import com.kvs.exam.service.ExamSetupService;
import com.kvs.exam.service.MarkEntryService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Module 05 assessments (examservice). Students, classes and sections are read from the studentservice tables.
 * Covers user stories:
 * - US-05000: Configure assessment terms, subject weightage ratios, and grading scales
 * - US-05003: Enter student scholastic scores via responsive web mark entry grids
 * - US-05005: Lock examination score entry screens automatically upon timer expiration (T_lock)
 * - US-05006: Submit formal ticket requests to unlock locked mark sheets for correction
 * - US-05007: Compile master broadsheets aggregating formative and summative assessments
 * - US-05008: Evaluate co-scholastic rubrics covering sports, arts, and value education
 * - US-05009: Synthesize multi-dimensional data into NEP 2020-compliant holistic progress cards
 * - US-05010: Publish digital report cards directly to parent mobile application portals
 * - US-05011: Generate class rank lists, subject topper reports, and percentile bands
 * - US-05013: Track term-on-term academic progress trends using comparative bar charts
 * - US-05016: Flag incomplete grade entries before initiating final report card compilation
 * - US-05018: Attach custom teacher narrative remarks to individual student progress cards
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/exams")
public class ExamController {

    @Autowired private ExamSetupService setupService;
    @Autowired private MarkEntryService markEntryService;
    @Autowired private ExamResultService resultService;

    // ------------------------------------------------------------------ US-05000 setup

    @PostMapping("/grading-scales")
    @PreAuthorize(ExamRoles.CONFIGURE)
    public ResponseEntity<ApiResponse<GradingScaleResponse>> createScale(@Valid @RequestBody CreateGradingScaleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Grading scale created", setupService.createScale(request)));
    }

    @GetMapping("/grading-scales")
    @PreAuthorize(ExamRoles.VIEW)
    public ResponseEntity<ApiResponse<List<GradingScaleResponse>>> listScales(@RequestParam(value = "tenantId", required = false) String tenantId) {
        return ResponseEntity.ok(ApiResponse.success(setupService.listScales(tenantId)));
    }

    @PostMapping("/terms")
    @PreAuthorize(ExamRoles.CONFIGURE)
    public ResponseEntity<ApiResponse<ExamTermResponse>> createTerm(@Valid @RequestBody CreateExamTermRequest request) {
        log.info("Request to create exam term {}", request.getTermName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Exam term created", setupService.createTerm(request)));
    }

    @GetMapping("/terms")
    @PreAuthorize(ExamRoles.STUDENT_PROGRESS)
    public ResponseEntity<ApiResponse<List<ExamTermResponse>>> listTerms(
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "academicYearId", required = false) UUID academicYearId) {
        return ResponseEntity.ok(ApiResponse.success(setupService.listTerms(tenantId, academicYearId)));
    }

    @GetMapping("/terms/{examTermId}")
    @PreAuthorize(ExamRoles.STUDENT_PROGRESS)
    public ResponseEntity<ApiResponse<ExamTermResponse>> getTerm(@PathVariable("examTermId") UUID examTermId) {
        return ResponseEntity.ok(ApiResponse.success(setupService.getTerm(examTermId)));
    }

    @PostMapping("/terms/{examTermId}/subjects")
    @PreAuthorize(ExamRoles.CONFIGURE)
    public ResponseEntity<ApiResponse<List<ExamSubjectResponse>>> addSubjects(@PathVariable("examTermId") UUID examTermId,
                                                                              @Valid @RequestBody AddExamSubjectsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Subjects configured", setupService.addSubjects(examTermId, request)));
    }

    @GetMapping("/terms/{examTermId}/subjects")
    @PreAuthorize(ExamRoles.VIEW)
    public ResponseEntity<ApiResponse<List<ExamSubjectResponse>>> listSubjects(@PathVariable("examTermId") UUID examTermId,
                                                                               @RequestParam(value = "gradeId", required = false) UUID gradeId) {
        return ResponseEntity.ok(ApiResponse.success(setupService.listSubjects(examTermId, gradeId)));
    }

    /** US-05005: lock mark entry now */
    @PostMapping("/terms/{examTermId}/lock")
    @PreAuthorize(ExamRoles.CONFIGURE)
    public ResponseEntity<ApiResponse<ExamTermResponse>> lockTerm(@PathVariable("examTermId") UUID examTermId) {
        return ResponseEntity.ok(ApiResponse.success("Mark entry locked", setupService.lockTerm(examTermId)));
    }

    // ------------------------------------------------------------------ US-05003 / US-05006 / US-05016

    @GetMapping("/terms/{examTermId}/marks")
    @PreAuthorize(ExamRoles.VIEW)
    public ResponseEntity<ApiResponse<MarkSheetResponse>> markSheet(@PathVariable("examTermId") UUID examTermId,
                                                                    @RequestParam(value = "sectionId", required = false) UUID sectionId,
                                                                    @RequestParam(value = "subjectCode", required = false) String subjectCode,
                                                                    @RequestParam(value = "branchId", required = false) String branchId) {
        return ResponseEntity.ok(ApiResponse.success(markEntryService.markSheet(examTermId, require(sectionId, "sectionId"), require(subjectCode, "subjectCode"), branchId)));
    }

    @PutMapping("/terms/{examTermId}/marks")
    @PreAuthorize(ExamRoles.ENTER_MARKS)
    public ResponseEntity<ApiResponse<MarkSheetResponse>> saveMarks(@PathVariable("examTermId") UUID examTermId,
                                                                    @RequestParam(value = "branchId", required = false) String branchId,
                                                                    @Valid @RequestBody SaveMarksRequest request) {
        log.info("Marks for term {} section {} subject {}", examTermId, request.getSectionId(), request.getSubjectCode());
        return ResponseEntity.ok(ApiResponse.success("Marks saved", markEntryService.saveMarks(examTermId, request, branchId)));
    }

    @PostMapping("/terms/{examTermId}/unlock-tickets")
    @PreAuthorize(ExamRoles.REQUEST_UNLOCK)
    public ResponseEntity<ApiResponse<UnlockTicketResponse>> requestUnlock(@PathVariable("examTermId") UUID examTermId,
                                                                           @Valid @RequestBody UnlockTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Unlock ticket raised", markEntryService.requestUnlock(examTermId, request)));
    }

    @GetMapping("/terms/{examTermId}/unlock-tickets")
    @PreAuthorize(ExamRoles.VIEW)
    public ResponseEntity<ApiResponse<List<UnlockTicketResponse>>> listTickets(@PathVariable("examTermId") UUID examTermId) {
        return ResponseEntity.ok(ApiResponse.success(markEntryService.listTickets(examTermId)));
    }

    @PatchMapping("/unlock-tickets/{ticketId}/decision")
    @PreAuthorize(ExamRoles.DECIDE_UNLOCK)
    public ResponseEntity<ApiResponse<UnlockTicketResponse>> decide(@PathVariable("ticketId") UUID ticketId,
                                                                    @Valid @RequestBody UnlockDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Unlock ticket " + request.getDecision().name().toLowerCase(), markEntryService.decide(ticketId, request)));
    }

    @GetMapping("/terms/{examTermId}/completeness")
    @PreAuthorize(ExamRoles.VIEW)
    public ResponseEntity<ApiResponse<MarkCompletenessResponse>> completeness(@PathVariable("examTermId") UUID examTermId,
                                                                              @RequestParam(value = "sectionId", required = false) UUID sectionId,
                                                                              @RequestParam(value = "gradeId", required = false) UUID gradeId,
                                                                              @RequestParam(value = "branchId", required = false) String branchId) {
        return ResponseEntity.ok(ApiResponse.success(markEntryService.completeness(examTermId, sectionId, gradeId, branchId)));
    }

    // ------------------------------------------------------------------ results

    @GetMapping("/terms/{examTermId}/broadsheet")
    @PreAuthorize(ExamRoles.VIEW)
    public ResponseEntity<ApiResponse<BroadsheetResponse>> broadsheet(@PathVariable("examTermId") UUID examTermId,
                                                                      @RequestParam(value = "sectionId", required = false) UUID sectionId,
                                                                      @RequestParam(value = "branchId", required = false) String branchId) {
        return ResponseEntity.ok(ApiResponse.success(resultService.broadsheet(examTermId, require(sectionId, "sectionId"), branchId)));
    }

    @GetMapping("/terms/{examTermId}/rank-list")
    @PreAuthorize(ExamRoles.VIEW)
    public ResponseEntity<ApiResponse<RankListResponse>> rankList(@PathVariable("examTermId") UUID examTermId,
                                                                  @RequestParam(value = "gradeId", required = false) UUID gradeId,
                                                                  @RequestParam(value = "sectionId", required = false) UUID sectionId,
                                                                  @RequestParam(value = "branchId", required = false) String branchId) {
        return ResponseEntity.ok(ApiResponse.success(resultService.rankList(examTermId, gradeId, sectionId, branchId)));
    }

    @PutMapping("/terms/{examTermId}/co-scholastic")
    @PreAuthorize(ExamRoles.CO_SCHOLASTIC)
    public ResponseEntity<ApiResponse<CoScholasticResponse>> saveCoScholastic(@PathVariable("examTermId") UUID examTermId,
                                                                              @Valid @RequestBody SaveCoScholasticRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Co-scholastic grades saved", resultService.saveCoScholastic(examTermId, request)));
    }

    @GetMapping("/terms/{examTermId}/co-scholastic")
    @PreAuthorize(ExamRoles.VIEW)
    public ResponseEntity<ApiResponse<CoScholasticResponse>> coScholastic(@PathVariable("examTermId") UUID examTermId,
                                                                          @RequestParam(value = "sectionId", required = false) UUID sectionId,
                                                                          @RequestParam(value = "branchId", required = false) String branchId) {
        return ResponseEntity.ok(ApiResponse.success(resultService.coScholastic(examTermId, require(sectionId, "sectionId"), branchId)));
    }

    @PostMapping("/terms/{examTermId}/publish")
    @PreAuthorize(ExamRoles.PUBLISH)
    public ResponseEntity<ApiResponse<PublishResultsResponse>> publish(@PathVariable("examTermId") UUID examTermId,
                                                                       @RequestParam(value = "branchId", required = false) String branchId,
                                                                       @Valid @RequestBody PublishResultsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Progress cards published", resultService.publish(examTermId, request.getSectionId(), branchId)));
    }

    @GetMapping("/students/{studentId}/progress-card")
    @PreAuthorize(ExamRoles.STUDENT_PROGRESS)
    public ResponseEntity<ApiResponse<ProgressCardResponse>> progressCard(@PathVariable("studentId") UUID studentId,
                                                                          @RequestParam(value = "examTermId", required = false) UUID examTermId) {
        return ResponseEntity.ok(ApiResponse.success(resultService.progressCard(studentId, require(examTermId, "examTermId"))));
    }

    @PutMapping("/students/{studentId}/progress-card/remarks")
    @PreAuthorize(ExamRoles.REMARKS)
    public ResponseEntity<ApiResponse<ProgressCardResponse>> saveRemarks(@PathVariable("studentId") UUID studentId,
                                                                         @Valid @RequestBody ProgressRemarksRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Remarks saved", resultService.saveRemarks(studentId, request)));
    }

    @GetMapping("/students/{studentId}/progress-trend")
    @PreAuthorize(ExamRoles.STUDENT_PROGRESS)
    public ResponseEntity<ApiResponse<ProgressTrendResponse>> progressTrend(@PathVariable("studentId") UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success(resultService.progressTrend(studentId)));
    }

    /** A missing query parameter answers 400 (not the generic 500 of MissingServletRequestParameterException). */
    private static <T> T require(T value, String name) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            throw new BadRequestException(name + " is required");
        }
        return value;
    }
}
