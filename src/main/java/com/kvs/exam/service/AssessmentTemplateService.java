package com.kvs.exam.service;

import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.exam.dto.ApplyTemplateRequest;
import com.kvs.exam.dto.ApplyTemplateResponse;
import com.kvs.exam.dto.AssessmentTemplateResponse;
import com.kvs.exam.dto.CreateExamTermRequest;
import com.kvs.exam.dto.CreateGradingScaleRequest;
import com.kvs.exam.dto.ExamTermResponse;
import com.kvs.exam.dto.GradingScaleResponse;
import com.kvs.exam.dto.SaveAssessmentTemplateRequest;
import com.kvs.exam.entity.AssessmentTemplate;
import com.kvs.exam.entity.GradeBand;
import com.kvs.exam.entity.TemplateArea;
import com.kvs.exam.entity.TemplateTerm;
import com.kvs.exam.repo.AssessmentTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Assessment templates per board: the master templates (read-only), a school's own editable copies, and applying a template
 * to an academic year (creates the school's grading scale and one exam term per template term).
 */
@Service
@Slf4j
@Transactional
public class AssessmentTemplateService {

    @Autowired private AssessmentTemplateRepository repository;
    @Autowired private ExamSetupService setupService;
    @Autowired private ExamAccess access;
    @Autowired private StudentAuditService auditService;

    /** The master templates and the school's copies, for a board when given (a state board sees the STATE templates too). */
    @Transactional(readOnly = true)
    public List<AssessmentTemplateResponse> list(String tenantId, String boardCode) {
        String tenant = access.tenantFor(tenantId);
        String board = boardCode == null ? "" : boardCode.trim().toUpperCase(Locale.ROOT);
        return repository.findVisible(tenant).stream()
                .filter(t -> board.isEmpty() || t.getBoardCode().equals(board) || ("STATE".equals(t.getBoardCode()) && board.startsWith("STATE")))
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AssessmentTemplateResponse get(UUID templateId, String tenantId) {
        return toResponse(visible(templateId, access.tenantFor(tenantId)));
    }

    /** A school's own copy of a template, ready to edit. */
    public AssessmentTemplateResponse copy(UUID templateId, String tenantId) {
        String tenant = access.tenantFor(tenantId);
        AssessmentTemplate source = visible(templateId, tenant);
        String base = source.getTemplateName().replaceAll(" \\(our copy( \\d+)?\\)$", "") + " (our copy)";
        String name = base;
        for (int n = 2; repository.existsByTenantIdAndTemplateNameIgnoreCase(tenant, name); n++) {
            name = base.substring(0, base.length() - 1) + " " + n + ")";
        }
        AssessmentTemplate copy = AssessmentTemplate.builder()
                .tenantId(tenant).boardCode(source.getBoardCode()).templateName(name).description(source.getDescription())
                .passPercent(source.getPassPercent()).needsReview(source.isNeedsReview()).reportLayout(source.getReportLayout())
                .sourceTemplateId(source.isMaster() ? source.getTemplateId() : source.getSourceTemplateId())
                .terms(new ArrayList<>(source.getTerms().stream().map(t -> TemplateTerm.builder().sortOrder(t.getSortOrder())
                        .termName(t.getTermName()).termType(t.getTermType()).weightagePercent(t.getWeightagePercent()).build()).toList()))
                .areas(new ArrayList<>(source.getAreas().stream().map(a -> TemplateArea.builder().areaKey(a.getAreaKey()).areaLabel(a.getAreaLabel()).build()).toList()))
                .bands(new ArrayList<>(source.getBands().stream().map(b -> GradeBand.builder().gradeLabel(b.getGradeLabel()).minPercent(b.getMinPercent())
                        .gradePoint(b.getGradePoint()).description(b.getDescription()).build()).toList()))
                .build();
        copy = repository.save(copy);
        auditService.logSuccess("assessment-template:" + copy.getTemplateId(), "ASSESSMENT_TEMPLATE_COPIED", name + " copied from " + source.getTemplateName());
        return toResponse(copy);
    }

    /** Edit the school's own copy; a master template cannot be edited. */
    public AssessmentTemplateResponse save(UUID templateId, String tenantId, SaveAssessmentTemplateRequest r) {
        String tenant = access.tenantFor(tenantId);
        AssessmentTemplate t = own(templateId, tenant);
        String name = r.getTemplateName().trim();
        if (!name.equalsIgnoreCase(t.getTemplateName()) && repository.existsByTenantIdAndTemplateNameIgnoreCase(tenant, name)) {
            throw new ConflictException("A template named " + name + " already exists");
        }
        List<TemplateTerm> terms = terms(r.getTerms());
        List<GradeBand> bands = bands(r.getBands());
        Set<String> keys = new HashSet<>();
        List<TemplateArea> areas = new ArrayList<>();
        for (SaveAssessmentTemplateRequest.Area a : r.getAreas() == null ? List.<SaveAssessmentTemplateRequest.Area>of() : r.getAreas()) {
            if (!keys.add(a.getAreaKey().trim().toUpperCase(Locale.ROOT))) throw new BadRequestException("Each co-scholastic area needs its own key");
            areas.add(TemplateArea.builder().areaKey(a.getAreaKey().trim().toUpperCase(Locale.ROOT)).areaLabel(a.getAreaLabel().trim()).build());
        }
        t.setTemplateName(name);
        t.setDescription(r.getDescription() == null ? null : r.getDescription().trim());
        t.setPassPercent(r.getPassPercent());
        t.setNeedsReview(false);
        t.getTerms().clear();
        t.getTerms().addAll(terms);
        t.getAreas().clear();
        t.getAreas().addAll(areas);
        t.getBands().clear();
        t.getBands().addAll(bands);
        repository.save(t);
        auditService.logSuccess("assessment-template:" + templateId, "ASSESSMENT_TEMPLATE_SAVED",
                name + ": " + terms.size() + " terms, " + bands.size() + " bands, pass " + r.getPassPercent() + "%");
        return toResponse(t);
    }

    public void delete(UUID templateId, String tenantId) {
        AssessmentTemplate t = own(templateId, access.tenantFor(tenantId));
        repository.delete(t);
        auditService.logSuccess("assessment-template:" + templateId, "ASSESSMENT_TEMPLATE_DELETED", t.getTemplateName());
    }

    /**
     * Use a template for an academic year: the school's grading scale (named like the template; reused when it exists) and one
     * exam term per template term, with the dates given. All or nothing: a clash (a term that already exists, weightage over 100%,
     * dates outside the year) creates nothing.
     */
    public ApplyTemplateResponse apply(UUID templateId, ApplyTemplateRequest request) {
        String tenant = access.tenantFor(request.getTenantId());
        AssessmentTemplate t = visible(templateId, tenant);
        if (t.getTerms().stream().mapToInt(TemplateTerm::getWeightagePercent).sum() != 100) {
            throw new BadRequestException("The term weightages of this template must add up to 100%");
        }
        List<ApplyTemplateRequest.TermDates> given = request.getTerms();
        for (TemplateTerm term : t.getTerms()) {
            if (given.stream().noneMatch(d -> d.getTermName().trim().equalsIgnoreCase(term.getTermName()))) {
                throw new BadRequestException("Give the dates of " + term.getTermName());
            }
        }
        GradingScaleResponse scale = setupService.listScales(tenant).stream()
                .filter(s -> s.getScaleName().equalsIgnoreCase(t.getTemplateName())).findFirst().orElse(null);
        boolean created = scale == null;
        if (created) {
            scale = setupService.createScale(CreateGradingScaleRequest.builder().tenantId(tenant).scaleName(t.getTemplateName())
                    .passPercent(t.getPassPercent())
                    .bands(t.getBands().stream().map(b -> CreateGradingScaleRequest.Band.builder().gradeLabel(b.getGradeLabel())
                            .minPercent(b.getMinPercent()).gradePoint(b.getGradePoint()).description(b.getDescription()).build()).toList())
                    .build());
        }
        List<ExamTermResponse> terms = new ArrayList<>();
        for (TemplateTerm term : t.getTerms()) {
            ApplyTemplateRequest.TermDates d = given.stream().filter(x -> x.getTermName().trim().equalsIgnoreCase(term.getTermName())).findFirst().orElseThrow();
            terms.add(setupService.createTerm(CreateExamTermRequest.builder().tenantId(tenant).academicYearId(request.getAcademicYearId())
                    .termName(term.getTermName()).termType(term.getTermType()).weightagePercent(term.getWeightagePercent())
                    .startDate(d.getStartDate()).endDate(d.getEndDate()).markEntryDeadline(d.getMarkEntryDeadline())
                    .gradingScaleId(scale.getGradingScaleId()).templateId(t.getTemplateId()).build()));
        }
        auditService.logSuccess("assessment-template:" + templateId, "ASSESSMENT_TEMPLATE_APPLIED",
                t.getTemplateName() + " applied to " + request.getAcademicYearId() + ": " + terms.size() + " terms" + (created ? ", new grading scale" : ""));
        return ApplyTemplateResponse.builder().gradingScaleId(scale.getGradingScaleId()).scaleName(scale.getScaleName())
                .scaleCreated(created).terms(terms).build();
    }

    // ------------------------------------------------------------------ helpers

    private AssessmentTemplate visible(UUID templateId, String tenant) {
        AssessmentTemplate t = repository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("AssessmentTemplate", "templateId", templateId));
        if (!t.isMaster() && !tenant.equals(t.getTenantId())) throw new ResourceNotFoundException("AssessmentTemplate", "templateId", templateId);
        return t;
    }

    private AssessmentTemplate own(UUID templateId, String tenant) {
        AssessmentTemplate t = visible(templateId, tenant);
        if (t.isMaster()) throw new BadRequestException("A master template cannot be changed: make a copy for your school first");
        return t;
    }

    private List<TemplateTerm> terms(List<SaveAssessmentTemplateRequest.Term> in) {
        Set<String> names = new HashSet<>();
        List<TemplateTerm> out = new ArrayList<>();
        int total = 0;
        for (SaveAssessmentTemplateRequest.Term t : in) {
            if (!names.add(t.getTermName().trim().toUpperCase(Locale.ROOT))) throw new BadRequestException("Each term needs its own name");
            total += t.getWeightagePercent();
            out.add(TemplateTerm.builder().sortOrder(out.size() + 1).termName(t.getTermName().trim()).termType(t.getTermType())
                    .weightagePercent(t.getWeightagePercent()).build());
        }
        if (total != 100) throw new BadRequestException("The term weightages must add up to 100% (now " + total + "%)");
        return out;
    }

    private List<GradeBand> bands(List<SaveAssessmentTemplateRequest.Band> in) {
        Set<String> labels = new HashSet<>();
        Set<BigDecimal> mins = new HashSet<>();
        for (SaveAssessmentTemplateRequest.Band b : in) {
            if (!labels.add(b.getGradeLabel().trim().toUpperCase(Locale.ROOT)) || !mins.add(b.getMinPercent().stripTrailingZeros())) {
                throw new BadRequestException("Each band needs its own grade label and minimum percentage");
            }
        }
        if (in.stream().noneMatch(b -> b.getMinPercent().signum() == 0)) {
            throw new BadRequestException("One band must start at 0% so every percentage gets a grade");
        }
        return new ArrayList<>(in.stream().sorted(Comparator.comparing(SaveAssessmentTemplateRequest.Band::getMinPercent).reversed())
                .map(b -> GradeBand.builder().gradeLabel(b.getGradeLabel().trim()).minPercent(b.getMinPercent()).gradePoint(b.getGradePoint())
                        .description(b.getDescription()).build()).toList());
    }

    private AssessmentTemplateResponse toResponse(AssessmentTemplate t) {
        return AssessmentTemplateResponse.builder()
                .templateId(t.getTemplateId()).templateCode(t.getTemplateCode()).boardCode(t.getBoardCode()).templateName(t.getTemplateName())
                .description(t.getDescription()).passPercent(t.getPassPercent()).needsReview(t.isNeedsReview()).reportLayout(t.getReportLayout())
                .master(t.isMaster()).sourceTemplateId(t.getSourceTemplateId())
                .totalWeightage(t.getTerms().stream().mapToInt(TemplateTerm::getWeightagePercent).sum())
                .terms(t.getTerms().stream().map(x -> AssessmentTemplateResponse.Term.builder().termName(x.getTermName()).termType(x.getTermType())
                        .weightagePercent(x.getWeightagePercent()).build()).toList())
                .areas(t.getAreas().stream().map(a -> AssessmentTemplateResponse.Area.builder().areaKey(a.getAreaKey()).areaLabel(a.getAreaLabel()).build()).toList())
                .bands(t.getBands().stream().map(b -> AssessmentTemplateResponse.Band.builder().gradeLabel(b.getGradeLabel()).minPercent(b.getMinPercent())
                        .gradePoint(b.getGradePoint()).description(b.getDescription()).build()).toList())
                .build();
    }
}
