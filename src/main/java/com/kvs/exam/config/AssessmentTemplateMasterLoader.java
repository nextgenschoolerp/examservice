package com.kvs.exam.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kvs.exam.entity.AssessmentTemplate;
import com.kvs.exam.entity.GradeBand;
import com.kvs.exam.entity.TemplateArea;
import com.kvs.exam.entity.TemplateTerm;
import com.kvs.exam.enums.ExamTermType;
import com.kvs.exam.repo.AssessmentTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Master data loader: writes the templates of master/assessment-templates.json that are missing from assessment_templates
 * (a master template already there, including one edited in the database, is left as it is). Safe to run on every start.
 */
@Slf4j
@Component
public class AssessmentTemplateMasterLoader implements ApplicationRunner {

    private final AssessmentTemplateRepository repository;
    private final ObjectMapper mapper;

    public AssessmentTemplateMasterLoader(AssessmentTemplateRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        int added = 0;
        try (InputStream in = new ClassPathResource("master/assessment-templates.json").getInputStream()) {
            for (JsonNode n : mapper.readTree(in).get("templates")) {
                String code = n.get("code").asText();
                if (repository.existsByTemplateCode(code)) continue;
                List<TemplateTerm> terms = new ArrayList<>();
                for (JsonNode t : n.get("terms")) {
                    terms.add(TemplateTerm.builder().sortOrder(terms.size() + 1).termName(t.get("name").asText())
                            .termType(ExamTermType.valueOf(t.get("type").asText())).weightagePercent(t.get("weightage").asInt()).build());
                }
                List<TemplateArea> areas = new ArrayList<>();
                for (JsonNode a : n.get("areas")) {
                    areas.add(TemplateArea.builder().areaKey(a.get("key").asText()).areaLabel(a.get("label").asText()).build());
                }
                List<GradeBand> bands = new ArrayList<>();
                for (JsonNode b : n.get("bands")) {
                    bands.add(GradeBand.builder().gradeLabel(b.get("label").asText()).minPercent(new BigDecimal(b.get("min").asText()))
                            .gradePoint(new BigDecimal(b.get("point").asText())).description(b.get("description").asText()).build());
                }
                repository.save(AssessmentTemplate.builder().templateCode(code).boardCode(n.get("boardCode").asText())
                        .templateName(n.get("name").asText()).description(n.get("description").asText())
                        .passPercent(new BigDecimal(n.get("passPercent").asText())).needsReview(n.get("needsReview").asBoolean())
                        .reportLayout(n.get("reportLayout").asText()).terms(terms).areas(areas).bands(bands).build());
                added++;
            }
        }
        log.info("Assessment template master data: {} added", added);
    }
}
