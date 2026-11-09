package com.kvs.exam.controller;

import com.kvs.exam.config.ExamPerms;
import com.kvs.core.dto.ApiResponse;
import com.kvs.exam.dto.ApplyTemplateRequest;
import com.kvs.exam.dto.ApplyTemplateResponse;
import com.kvs.exam.dto.AssessmentTemplateResponse;
import com.kvs.exam.dto.SaveAssessmentTemplateRequest;
import com.kvs.exam.service.AssessmentTemplateService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Assessment templates per board (Settings → Academic setup → Assessment templates). Anyone who can view the exam setup reads
 * them; the exam controller, school principal and super administrator copy, edit and apply them.
 */
@RestController
@RequestMapping("/api/v1/exams/templates")
public class AssessmentTemplateController {

    @Autowired private AssessmentTemplateService service;

    @GetMapping
    @PreAuthorize(ExamPerms.TERMS_VIEW)
    public ResponseEntity<ApiResponse<List<AssessmentTemplateResponse>>> list(@RequestParam(value = "tenantId", required = false) String tenantId,
                                                                              @RequestParam(value = "boardCode", required = false) String boardCode) {
        return ResponseEntity.ok(ApiResponse.success(service.list(tenantId, boardCode)));
    }

    @GetMapping("/{templateId}")
    @PreAuthorize(ExamPerms.TERMS_VIEW)
    public ResponseEntity<ApiResponse<AssessmentTemplateResponse>> get(@PathVariable("templateId") UUID templateId,
                                                                       @RequestParam(value = "tenantId", required = false) String tenantId) {
        return ResponseEntity.ok(ApiResponse.success(service.get(templateId, tenantId)));
    }

    @PostMapping("/{templateId}/copy")
    @PreAuthorize(ExamPerms.TEMPLATES_MANAGE)
    public ResponseEntity<ApiResponse<AssessmentTemplateResponse>> copy(@PathVariable("templateId") UUID templateId,
                                                                        @RequestParam(value = "tenantId", required = false) String tenantId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Template copied", service.copy(templateId, tenantId)));
    }

    @PutMapping("/{templateId}")
    @PreAuthorize(ExamPerms.TEMPLATES_MANAGE)
    public ResponseEntity<ApiResponse<AssessmentTemplateResponse>> save(@PathVariable("templateId") UUID templateId,
                                                                        @RequestParam(value = "tenantId", required = false) String tenantId,
                                                                        @Valid @RequestBody SaveAssessmentTemplateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Template saved", service.save(templateId, tenantId, request)));
    }

    @DeleteMapping("/{templateId}")
    @PreAuthorize(ExamPerms.TEMPLATES_MANAGE)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("templateId") UUID templateId,
                                                    @RequestParam(value = "tenantId", required = false) String tenantId) {
        service.delete(templateId, tenantId);
        return ResponseEntity.ok(ApiResponse.success("Template deleted", null));
    }

    @PostMapping("/{templateId}/apply")
    @PreAuthorize(ExamPerms.TEMPLATES_MANAGE)
    public ResponseEntity<ApiResponse<ApplyTemplateResponse>> apply(@PathVariable("templateId") UUID templateId,
                                                                    @Valid @RequestBody ApplyTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Template applied", service.apply(templateId, request)));
    }
}
