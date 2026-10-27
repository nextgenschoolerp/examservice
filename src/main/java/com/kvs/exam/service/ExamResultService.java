package com.kvs.exam.service;

import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.exam.dto.BroadsheetResponse;
import com.kvs.exam.dto.CoScholasticResponse;
import com.kvs.exam.dto.MarkCompletenessResponse;
import com.kvs.exam.dto.ProgressCardResponse;
import com.kvs.exam.dto.ProgressRemarksRequest;
import com.kvs.exam.dto.ProgressTrendResponse;
import com.kvs.exam.dto.PublishResultsResponse;
import com.kvs.exam.dto.RankListResponse;
import com.kvs.exam.dto.SaveCoScholasticRequest;
import com.kvs.exam.entity.AcademicSession;
import com.kvs.exam.entity.CoScholasticGrade;
import com.kvs.exam.entity.ExamSubject;
import com.kvs.exam.entity.ExamTerm;
import com.kvs.exam.entity.GradeBand;
import com.kvs.exam.entity.GradingScale;
import com.kvs.exam.entity.MarkEntry;
import com.kvs.exam.entity.ProgressCard;
import com.kvs.exam.entity.Section;
import com.kvs.exam.entity.StudentMaster;
import com.kvs.exam.repo.AcademicSessionRepository;
import com.kvs.exam.repo.CoScholasticGradeRepository;
import com.kvs.exam.repo.ExamSubjectRepository;
import com.kvs.exam.repo.ExamTermRepository;
import com.kvs.exam.repo.GradeRepository;
import com.kvs.exam.repo.GradingScaleRepository;
import com.kvs.exam.repo.MarkEntryRepository;
import com.kvs.exam.repo.ProgressCardRepository;
import com.kvs.exam.repo.SectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Results, progress cards and reports.
 * Covers user stories:
 * - US-05007: Compile master broadsheets aggregating formative and summative assessments (Academic Auditor)
 * - US-05008: Evaluate co-scholastic rubrics covering sports, arts, and value education (Exam Controller)
 * - US-05009: Synthesize multi-dimensional data into NEP 2020-compliant holistic progress cards (Subject Teacher)
 * - US-05010: Publish digital report cards directly to parent mobile application portals (Class Teacher)
 * - US-05011: Generate class rank lists, subject topper reports, and percentile bands (Academic Principal)
 * - US-05013: Track term-on-term academic progress trends using comparative bar charts (Parent)
 * - US-05018: Attach custom teacher narrative remarks to individual student progress cards (Class Teacher)
 */
@Service
@Slf4j
@Transactional
public class ExamResultService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Autowired private ExamTermRepository termRepository;
    @Autowired private ExamSubjectRepository subjectRepository;
    @Autowired private MarkEntryRepository markRepository;
    @Autowired private GradingScaleRepository scaleRepository;
    @Autowired private CoScholasticGradeRepository coScholasticRepository;
    @Autowired private ProgressCardRepository cardRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private AcademicSessionRepository sessionRepository;
    @Autowired private MarkEntryService markEntryService;
    @Autowired private ExamAccess access;
    @Autowired private StudentAuditService auditService;

    // ------------------------------------------------------------------ result computation

    /** One student's result in a term. */
    record StudentResult(StudentMaster student, List<SubjectScore> scores, BigDecimal totalObtained, BigDecimal totalMax,
                         BigDecimal percentage, GradeBand band, String result) {
        String gradeLabel() {
            return band == null ? null : band.getGradeLabel();
        }
    }

    record SubjectScore(ExamSubject subject, MarkEntry entry, BigDecimal percentage, GradeBand band, boolean passed) {
    }

    private GradingScale scale(ExamTerm term) {
        return scaleRepository.findById(term.getGradingScaleId())
                .orElseThrow(() -> new ResourceNotFoundException("GradingScale", "gradingScaleId", term.getGradingScaleId()));
    }

    static GradeBand band(GradingScale scale, BigDecimal percent) {
        if (percent == null) {
            return null;
        }
        return scale.getBands().stream().sorted(Comparator.comparing(GradeBand::getMinPercent).reversed())
                .filter(b -> percent.compareTo(b.getMinPercent()) >= 0).findFirst().orElse(null);
    }

    /**
     * Term percentage = subject percentages weighted by subject weightage (absent counts as 0).
     * PASS needs every subject at or above its pass marks and the term percentage at or above the scale's pass percent;
     * INCOMPLETE while a subject has no entry.
     */
    private StudentResult compute(StudentMaster s, List<ExamSubject> subjects, Map<UUID, MarkEntry> marks, GradingScale scale) {
        List<SubjectScore> scores = new ArrayList<>();
        BigDecimal obtained = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal weights = BigDecimal.ZERO;
        boolean complete = true;
        boolean allPassed = true;
        for (ExamSubject subject : subjects) {
            MarkEntry m = marks.get(subject.getExamSubjectId());
            if (m == null) {
                complete = false;
                scores.add(new SubjectScore(subject, null, null, null, false));
                continue;
            }
            BigDecimal got = m.getAbsent() ? BigDecimal.ZERO : m.getMarksObtained();
            BigDecimal pct = got.multiply(HUNDRED).divide(subject.getMaxMarks(), 2, RoundingMode.HALF_UP);
            boolean passed = !m.getAbsent() && got.compareTo(subject.getPassMarks()) >= 0;
            allPassed &= passed;
            obtained = obtained.add(got);
            max = max.add(subject.getMaxMarks());
            weighted = weighted.add(pct.multiply(subject.getWeightage()));
            weights = weights.add(subject.getWeightage());
            scores.add(new SubjectScore(subject, m, pct, band(scale, pct), passed));
        }
        BigDecimal percent = weights.signum() == 0 ? null : weighted.divide(weights, 2, RoundingMode.HALF_UP);
        String result = !complete || subjects.isEmpty() ? "INCOMPLETE"
                : allPassed && percent.compareTo(scale.getPassPercent()) >= 0 ? "PASS" : "FAIL";
        return new StudentResult(s, scores, obtained, max, percent, band(scale, percent), result);
    }

    /** Results of a section's students for a term, with competition ranks (1, 2, 2, 4) by percentage. */
    private List<StudentResult> sectionResults(ExamTerm term, Section section, String branchId, Map<UUID, Integer> ranksOut) {
        List<StudentMaster> roster = access.roster(section, term, branchId);
        return results(term, section.getGradeId(), roster, ranksOut);
    }

    private List<StudentResult> results(ExamTerm term, UUID gradeId, List<StudentMaster> students, Map<UUID, Integer> ranksOut) {
        List<ExamSubject> subjects = subjectRepository.findByExamTermIdAndGradeIdOrderByDisplayOrderAsc(term.getExamTermId(), gradeId);
        Map<UUID, Map<UUID, MarkEntry>> marks = markRepository.findByExamSubjectIdIn(subjects.stream().map(ExamSubject::getExamSubjectId).toList())
                .stream().collect(Collectors.groupingBy(MarkEntry::getStudentId,
                        Collectors.toMap(MarkEntry::getExamSubjectId, Function.identity(), (a, b) -> a)));
        GradingScale scale = scale(term);
        List<StudentResult> results = students.stream()
                .map(s -> compute(s, subjects, marks.getOrDefault(s.getStudentId(), Map.of()), scale)).toList();
        if (ranksOut != null) {
            ranksOut.putAll(rank(results));
        }
        return results;
    }

    private static Map<UUID, Integer> rank(List<StudentResult> results) {
        List<StudentResult> ranked = results.stream().filter(r -> r.percentage() != null)
                .sorted(Comparator.comparing(StudentResult::percentage).reversed()).toList();
        Map<UUID, Integer> ranks = new LinkedHashMap<>();
        for (StudentResult r : ranked) {
            ranks.put(r.student().getStudentId(),
                    1 + (int) ranked.stream().filter(o -> o.percentage().compareTo(r.percentage()) > 0).count());
        }
        return ranks;
    }

    // ------------------------------------------------------------------ US-05007 broadsheet

    @Transactional(readOnly = true)
    public BroadsheetResponse broadsheet(UUID examTermId, UUID sectionId, String branchId) {
        ExamTerm term = access.term(examTermId);
        Section section = access.section(sectionId, term.getTenantId());
        Map<UUID, Integer> ranks = new LinkedHashMap<>();
        List<StudentResult> results = sectionResults(term, section, branchId, ranks);
        List<ExamSubject> subjects = subjectRepository.findByExamTermIdAndGradeIdOrderByDisplayOrderAsc(examTermId, section.getGradeId());
        List<BroadsheetResponse.SubjectSummary> summaries = subjects.stream().map(subject -> {
            List<SubjectScore> scores = results.stream().flatMap(r -> r.scores().stream())
                    .filter(sc -> sc.subject().getExamSubjectId().equals(subject.getExamSubjectId()) && sc.entry() != null).toList();
            List<BigDecimal> got = scores.stream().map(sc -> sc.entry().getAbsent() ? BigDecimal.ZERO : sc.entry().getMarksObtained()).toList();
            return BroadsheetResponse.SubjectSummary.builder()
                    .subjectCode(subject.getSubjectCode()).subjectName(subject.getSubjectName()).maxMarks(subject.getMaxMarks()).passMarks(subject.getPassMarks())
                    .averageMarks(got.isEmpty() ? null : got.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(got.size()), 2, RoundingMode.HALF_UP))
                    .highestMarks(got.stream().max(Comparator.naturalOrder()).orElse(null))
                    .passed((int) scores.stream().filter(SubjectScore::passed).count())
                    .failed((int) scores.stream().filter(sc -> !sc.passed()).count())
                    .build();
        }).toList();
        List<BigDecimal> pcts = results.stream().map(StudentResult::percentage).filter(Objects::nonNull).toList();
        return BroadsheetResponse.builder()
                .examTermId(examTermId).termName(term.getTermName()).sectionId(sectionId).className(access.className(section))
                .classAveragePercent(pcts.isEmpty() ? null : pcts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(pcts.size()), 2, RoundingMode.HALF_UP))
                .subjects(summaries)
                .rows(results.stream().map(r -> BroadsheetResponse.Row.builder()
                        .studentId(r.student().getStudentId()).studentUid(r.student().getStudentUid())
                        .studentName(ExamAccess.fullName(r.student())).rollNumber(r.student().getRollNumber())
                        .marks(r.scores().stream().map(sc -> BroadsheetResponse.Cell.builder()
                                .subjectCode(sc.subject().getSubjectCode()).entered(sc.entry() != null)
                                .marksObtained(sc.entry() == null ? null : sc.entry().getMarksObtained())
                                .absent(sc.entry() != null && sc.entry().getAbsent())
                                .grade(sc.band() == null ? null : sc.band().getGradeLabel()).build()).toList())
                        .totalObtained(r.totalObtained()).totalMax(r.totalMax()).percentage(r.percentage())
                        .grade(r.gradeLabel()).result(r.result()).rank(ranks.get(r.student().getStudentId()))
                        .build()).toList())
                .build();
    }

    /**
     * The broadsheet of every section that has students on roll, in grade order: school-wide results of one term in a single
     * call (Academics outlook). Each entry is the same broadsheet the Broadsheet tab shows for that section.
     */
    @Transactional(readOnly = true)
    public List<BroadsheetResponse> schoolBroadsheets(UUID examTermId, String branchId) {
        ExamTerm term = access.term(examTermId);
        List<BroadsheetResponse> sheets = new ArrayList<>();
        for (com.kvs.exam.entity.Grade grade : gradeRepository.findByTenantIdOrderBySequenceOrderAsc(term.getTenantId())) {
            for (Section section : sectionRepository.findByGradeId(grade.getGradeId())) {
                if (!access.roster(section, term, branchId).isEmpty()) {
                    sheets.add(broadsheet(examTermId, section.getSectionId(), branchId));
                }
            }
        }
        return sheets;
    }

    // ------------------------------------------------------------------ US-05011 rank list

    @Transactional(readOnly = true)
    public RankListResponse rankList(UUID examTermId, UUID gradeId, UUID sectionId, String branchId) {
        ExamTerm term = access.term(examTermId);
        if ((gradeId == null) == (sectionId == null)) {
            throw new BadRequestException("Give either gradeId (whole class) or sectionId");
        }
        List<Section> sections = sectionId != null ? List.of(access.section(sectionId, term.getTenantId()))
                : sectionRepository.findByGradeId(access.grade(gradeId, term.getTenantId()).getGradeId());
        UUID grade = sections.isEmpty() ? gradeId : sections.get(0).getGradeId();
        Map<UUID, String> classOf = new LinkedHashMap<>();
        List<StudentMaster> students = new ArrayList<>();
        for (Section s : sections) {
            for (StudentMaster st : access.roster(s, term, branchId)) {
                students.add(st);
                classOf.put(st.getStudentId(), access.className(s));
            }
        }
        Map<UUID, Integer> ranks = new LinkedHashMap<>();
        List<StudentResult> results = results(term, grade, students, ranks);
        Map<UUID, StudentResult> byId = results.stream().collect(Collectors.toMap(r -> r.student().getStudentId(), Function.identity()));
        int n = ranks.size();
        List<RankListResponse.Entry> entries = ranks.entrySet().stream().map(e -> {
            StudentResult r = byId.get(e.getKey());
            double share = (double) e.getValue() / n;
            return RankListResponse.Entry.builder()
                    .rank(e.getValue()).studentId(e.getKey()).studentUid(r.student().getStudentUid())
                    .studentName(ExamAccess.fullName(r.student())).className(classOf.get(e.getKey()))
                    .percentage(r.percentage()).grade(r.gradeLabel())
                    .percentileBand(share <= 0.10 ? "Top 10%" : share <= 0.25 ? "Top 25%" : share <= 0.50 ? "Top 50%" : "Below median")
                    .build();
        }).toList();
        List<RankListResponse.Topper> toppers = subjectRepository.findByExamTermIdAndGradeIdOrderByDisplayOrderAsc(examTermId, grade).stream()
                .map(subject -> {
                    Map<String, BigDecimal> got = new LinkedHashMap<>();
                    results.forEach(r -> r.scores().stream()
                            .filter(sc -> sc.subject().getExamSubjectId().equals(subject.getExamSubjectId()) && sc.entry() != null && !sc.entry().getAbsent())
                            .forEach(sc -> got.put(ExamAccess.fullName(r.student()), sc.entry().getMarksObtained())));
                    BigDecimal top = got.values().stream().max(Comparator.naturalOrder()).orElse(null);
                    return RankListResponse.Topper.builder()
                            .subjectCode(subject.getSubjectCode()).subjectName(subject.getSubjectName()).maxMarks(subject.getMaxMarks())
                            .topMarks(top)
                            .students(top == null ? List.of() : got.entrySet().stream().filter(e -> e.getValue().compareTo(top) == 0)
                                    .map(Map.Entry::getKey).toList())
                            .build();
                }).toList();
        return RankListResponse.builder()
                .examTermId(examTermId).termName(term.getTermName())
                .scope(sectionId != null ? classOf.values().stream().findFirst().orElse("") : sections.isEmpty() ? "" :
                        access.className(sections.get(0)).replaceAll("-[^-]*$", "") + " (all sections)")
                .rankedStudents(n).ranks(entries).subjectToppers(toppers)
                .build();
    }

    // ------------------------------------------------------------------ US-05008 co-scholastic

    public CoScholasticResponse saveCoScholastic(UUID examTermId, SaveCoScholasticRequest request) {
        ExamTerm term = access.term(examTermId);
        String user = access.currentUser();
        Set<String> seen = new HashSet<>();
        Set<UUID> studentIds = new HashSet<>();
        for (SaveCoScholasticRequest.Entry e : request.getEntries()) {
            if (!seen.add(e.getStudentId() + "|" + e.getArea())) {
                throw new BadRequestException(e.getArea() + " appears twice for student " + e.getStudentId());
            }
            StudentMaster s = access.student(e.getStudentId());
            if (!s.getTenantId().equals(term.getTenantId())) {
                throw new ResourceNotFoundException("StudentMaster", "studentId", e.getStudentId());
            }
            CoScholasticGrade g = coScholasticRepository.findByExamTermIdAndStudentIdAndArea(examTermId, e.getStudentId(), e.getArea())
                    .orElseGet(() -> CoScholasticGrade.builder().examTermId(examTermId).studentId(e.getStudentId()).area(e.getArea()).build());
            g.setGrade(e.getGrade());
            g.setRemarks(e.getRemarks());
            g.setAssessedBy(user);
            coScholasticRepository.save(g);
            studentIds.add(e.getStudentId());
        }
        auditService.logSuccess("exam-term:" + examTermId, "CO_SCHOLASTIC_GRADED",
                request.getEntries().size() + " grades for " + studentIds.size() + " students");
        return coScholastic(term, studentIds.stream().map(access::student).toList());
    }

    @Transactional(readOnly = true)
    public CoScholasticResponse coScholastic(UUID examTermId, UUID sectionId, String branchId) {
        ExamTerm term = access.term(examTermId);
        Section section = access.section(sectionId, term.getTenantId());
        return coScholastic(term, access.roster(section, term, branchId));
    }

    private CoScholasticResponse coScholastic(ExamTerm term, List<StudentMaster> students) {
        Map<UUID, List<CoScholasticGrade>> grades = coScholasticRepository
                .findByExamTermIdAndStudentIdIn(term.getExamTermId(), students.stream().map(StudentMaster::getStudentId).toList())
                .stream().collect(Collectors.groupingBy(CoScholasticGrade::getStudentId));
        return CoScholasticResponse.builder()
                .examTermId(term.getExamTermId()).termName(term.getTermName())
                .rows(students.stream().map(s -> CoScholasticResponse.Row.builder()
                        .studentId(s.getStudentId()).studentUid(s.getStudentUid()).studentName(ExamAccess.fullName(s))
                        .grades(areaGrades(grades.getOrDefault(s.getStudentId(), List.of()))).build()).toList())
                .build();
    }

    private static List<CoScholasticResponse.AreaGrade> areaGrades(List<CoScholasticGrade> grades) {
        return grades.stream().sorted(Comparator.comparing(CoScholasticGrade::getArea))
                .map(g -> CoScholasticResponse.AreaGrade.builder().area(g.getArea()).grade(g.getGrade()).remarks(g.getRemarks()).build())
                .toList();
    }

    // ------------------------------------------------------------------ US-05018 remarks, US-05010 publish

    public ProgressCardResponse saveRemarks(UUID studentId, ProgressRemarksRequest request) {
        StudentMaster s = access.student(studentId);
        ExamTerm term = access.term(request.getExamTermId());
        ProgressCard card = cardRepository.findByExamTermIdAndStudentId(term.getExamTermId(), studentId)
                .orElseGet(() -> ProgressCard.builder().examTermId(term.getExamTermId()).studentId(studentId).build());
        if (card.getPublished()) {
            throw new ConflictException("The " + term.getTermName() + " progress card is already published; remarks can no longer change");
        }
        card.setTeacherRemarks(request.getRemarks().trim());
        card.setRemarksBy(access.currentUser());
        cardRepository.save(card);
        auditService.logSuccess("student:" + studentId, "PROGRESS_REMARKS_SAVED", term.getTermName() + " remarks for " + s.getStudentUid());
        return progressCard(studentId, term.getExamTermId());
    }

    /** Publishes the section's progress cards to parents; refused while any mark entry is missing (US-05016). */
    public PublishResultsResponse publish(UUID examTermId, UUID sectionId, String branchId) {
        ExamTerm term = access.term(examTermId);
        Section section = access.section(sectionId, term.getTenantId());
        MarkCompletenessResponse check = markEntryService.completeness(examTermId, sectionId, null, branchId);
        if (check.getExpectedEntries() == 0) {
            throw new ConflictException(access.className(section) + " has no students or subjects for " + term.getTermName());
        }
        if (!check.isComplete()) {
            throw new ConflictException(check.getMissingEntries() + " mark entr" + (check.getMissingEntries() == 1 ? "y is" : "ies are")
                    + " missing for " + access.className(section) + "; complete them before publishing");
        }
        List<StudentMaster> roster = access.roster(section, term, branchId);
        Map<UUID, ProgressCard> cards = cardRepository.findByExamTermIdAndStudentIdIn(examTermId,
                        roster.stream().map(StudentMaster::getStudentId).toList())
                .stream().collect(Collectors.toMap(ProgressCard::getStudentId, Function.identity()));
        Instant now = Instant.now();
        String user = access.currentUser();
        int published = 0;
        int already = 0;
        for (StudentMaster s : roster) {
            ProgressCard card = cards.getOrDefault(s.getStudentId(),
                    ProgressCard.builder().examTermId(examTermId).studentId(s.getStudentId()).build());
            if (card.getPublished()) {
                already++;
                continue;
            }
            card.setPublished(true);
            card.setPublishedAt(now);
            card.setPublishedBy(user);
            cardRepository.save(card);
            published++;
        }
        auditService.logSuccess("exam-term:" + examTermId, "PROGRESS_CARDS_PUBLISHED",
                access.className(section) + ": " + published + " published, " + already + " already published");
        return PublishResultsResponse.builder()
                .examTermId(examTermId).termName(term.getTermName()).sectionId(sectionId).className(access.className(section))
                .published(published).alreadyPublished(already).publishedAt(now)
                .build();
    }

    // ------------------------------------------------------------------ US-05009 progress card, US-05013 trend

    @Transactional(readOnly = true)
    public ProgressCardResponse progressCard(UUID studentId, UUID examTermId) {
        StudentMaster s = access.student(studentId);
        ExamTerm term = access.term(examTermId);
        ProgressCard card = cardRepository.findByExamTermIdAndStudentId(examTermId, studentId).orElse(null);
        if (access.isParentOnly() && (card == null || !card.getPublished())) {
            throw new ResourceNotFoundException("ProgressCard", "examTermId", examTermId);
        }
        // The class the student sat the term in, which stays on the card after a promotion
        Section section = access.sectionFor(s, term);
        if (section == null) {
            throw new BadRequestException(ExamAccess.fullName(s) + " is not placed in a section");
        }
        Map<UUID, Integer> ranks = new LinkedHashMap<>();
        List<StudentResult> results = sectionResults(term, section, s.getBranchId(), ranks);
        StudentResult r = results.stream().filter(x -> x.student().getStudentId().equals(studentId)).findFirst()
                .orElseGet(() -> results(term, section.getGradeId(), List.of(s), null).get(0));
        return ProgressCardResponse.builder()
                .studentId(studentId).studentUid(s.getStudentUid()).studentName(ExamAccess.fullName(s))
                .className(access.className(section)).rollNumber(s.getRollNumber())
                .examTermId(examTermId).termName(term.getTermName()).termType(term.getTermType())
                .academicYear(sessionRepository.findById(term.getAcademicYearId()).map(AcademicSession::getSessionName).orElse(null))
                .scholastic(r.scores().stream().map(sc -> ProgressCardResponse.SubjectResult.builder()
                        .subjectCode(sc.subject().getSubjectCode()).subjectName(sc.subject().getSubjectName())
                        .maxMarks(sc.subject().getMaxMarks()).entered(sc.entry() != null)
                        .marksObtained(sc.entry() == null ? null : sc.entry().getMarksObtained())
                        .absent(sc.entry() != null && sc.entry().getAbsent()).percentage(sc.percentage())
                        .grade(sc.band() == null ? null : sc.band().getGradeLabel()).passed(sc.passed()).build()).toList())
                .totalObtained(r.totalObtained()).totalMax(r.totalMax()).percentage(r.percentage())
                .grade(r.gradeLabel()).gradePoint(r.band() == null ? null : r.band().getGradePoint()).result(r.result())
                .sectionRank(ranks.get(studentId)).sectionSize(ranks.size())
                .coScholastic(areaGrades(coScholasticRepository.findByExamTermIdAndStudentIdIn(examTermId, List.of(studentId))))
                .teacherRemarks(card == null ? null : card.getTeacherRemarks()).remarksBy(card == null ? null : card.getRemarksBy())
                .published(card != null && card.getPublished()).publishedAt(card == null ? null : card.getPublishedAt())
                .build();
    }

    /**
     * Every term the student has marks in, oldest first, with the change against the previous term; the weighted
     * percentage uses the term weightages of the latest academic year. Parents only see published terms.
     */
    @Transactional(readOnly = true)
    public ProgressTrendResponse progressTrend(UUID studentId) {
        StudentMaster s = access.student(studentId);
        Section section = s.getSectionId() == null ? null : sectionRepository.findById(s.getSectionId()).orElse(null);
        Set<UUID> termsWithMarks = markRepository.findByStudentIdOrderByEnteredAtAsc(studentId).stream()
                .map(MarkEntry::getExamTermId).collect(Collectors.toSet());
        Set<UUID> published = cardRepository.findByStudentId(studentId).stream().filter(ProgressCard::getPublished)
                .map(ProgressCard::getExamTermId).collect(Collectors.toSet());
        boolean parent = access.isParentOnly();
        List<ExamTerm> terms = termRepository.findByTenantIdOrderByStartDateAsc(s.getTenantId()).stream()
                .filter(t -> termsWithMarks.contains(t.getExamTermId()))
                .filter(t -> !parent || published.contains(t.getExamTermId()))
                .toList();
        List<ProgressTrendResponse.Term> points = new ArrayList<>();
        BigDecimal previous = null;
        for (ExamTerm term : terms) {
            Map<UUID, Integer> ranks = new LinkedHashMap<>();
            StudentResult r;
            Section termSection = access.sectionFor(s, term);
            if (termSection != null) {
                r = sectionResults(term, termSection, s.getBranchId(), ranks).stream()
                        .filter(x -> x.student().getStudentId().equals(studentId)).findFirst().orElse(null);
            } else {
                r = null;
            }
            if (r == null) {
                UUID grade = subjectRepository.findByExamTermIdOrderByDisplayOrderAsc(term.getExamTermId()).stream()
                        .filter(sub -> markRepository.findByExamSubjectIdIn(List.of(sub.getExamSubjectId())).stream()
                                .anyMatch(m -> m.getStudentId().equals(studentId)))
                        .map(ExamSubject::getGradeId).findFirst().orElse(s.getGradeId());
                r = results(term, grade, List.of(s), null).get(0);
            }
            points.add(ProgressTrendResponse.Term.builder()
                    .examTermId(term.getExamTermId()).termName(term.getTermName()).termType(term.getTermType())
                    .className(termSection == null ? null : access.className(termSection))
                    .weightagePercent(term.getWeightagePercent()).startDate(term.getStartDate())
                    .percentage(r.percentage()).grade(r.gradeLabel()).result(r.result()).sectionRank(ranks.get(studentId))
                    .changeFromPrevious(previous == null || r.percentage() == null ? null : r.percentage().subtract(previous))
                    .published(published.contains(term.getExamTermId()))
                    .subjects(r.scores().stream().filter(sc -> sc.entry() != null).map(sc -> ProgressTrendResponse.SubjectPoint.builder()
                            .subjectCode(sc.subject().getSubjectCode()).subjectName(sc.subject().getSubjectName())
                            .percentage(sc.percentage()).grade(sc.band() == null ? null : sc.band().getGradeLabel()).build()).toList())
                    .build());
            if (r.percentage() != null) {
                previous = r.percentage();
            }
        }
        UUID latestYear = terms.isEmpty() ? null : terms.get(terms.size() - 1).getAcademicYearId();
        BigDecimal weighted = BigDecimal.ZERO;
        int weights = 0;
        for (int i = 0; i < terms.size(); i++) {
            if (terms.get(i).getAcademicYearId().equals(latestYear) && points.get(i).getPercentage() != null) {
                weighted = weighted.add(points.get(i).getPercentage().multiply(BigDecimal.valueOf(terms.get(i).getWeightagePercent())));
                weights += terms.get(i).getWeightagePercent();
            }
        }
        BigDecimal weightedPct = weights == 0 ? null : weighted.divide(BigDecimal.valueOf(weights), 2, RoundingMode.HALF_UP);
        BigDecimal last = points.isEmpty() ? null : points.get(points.size() - 1).getChangeFromPrevious();
        String trend = last == null ? "NOT_ENOUGH_DATA" : last.compareTo(BigDecimal.valueOf(2)) > 0 ? "IMPROVING"
                : last.compareTo(BigDecimal.valueOf(-2)) < 0 ? "DECLINING" : "STEADY";
        GradingScale scale = terms.isEmpty() ? null : scale(terms.get(terms.size() - 1));
        GradeBand weightedBand = scale == null ? null : band(scale, weightedPct);
        return ProgressTrendResponse.builder()
                .studentId(studentId).studentUid(s.getStudentUid()).studentName(ExamAccess.fullName(s))
                .className(section == null ? null : access.className(section))
                .weightedPercentage(weightedPct).weightedGrade(weightedBand == null ? null : weightedBand.getGradeLabel())
                .trend(trend).terms(points)
                .build();
    }
}
