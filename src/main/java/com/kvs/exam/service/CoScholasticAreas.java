package com.kvs.exam.service;

import com.kvs.exam.dto.CoScholasticResponse;
import com.kvs.exam.entity.AssessmentTemplate;
import com.kvs.exam.entity.ExamTerm;
import com.kvs.exam.entity.TemplateArea;
import com.kvs.exam.repo.AssessmentTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The co-scholastic areas graded for an exam term: those of the assessment template the term follows, or the CBSE / NEP 2020
 * defaults when it follows none (terms set up before templates existed keep working unchanged).
 */
@Component
public class CoScholasticAreas {

    /** key → label, in display order: the CBSE defaults */
    static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("ART_EDUCATION", "Art education");
        DEFAULTS.put("HEALTH_AND_PHYSICAL_EDUCATION", "Health & physical education");
        DEFAULTS.put("WORK_EDUCATION", "Work education");
        DEFAULTS.put("VALUE_EDUCATION", "Value education");
        DEFAULTS.put("DISCIPLINE", "Discipline");
    }

    @Autowired private AssessmentTemplateRepository templates;

    /** The term's areas: key → label, in the template's order */
    public Map<String, String> forTerm(ExamTerm term) {
        if (term.getTemplateId() == null) return DEFAULTS;
        AssessmentTemplate t = templates.findById(term.getTemplateId()).orElse(null);
        if (t == null || t.getAreas().isEmpty()) return t == null ? DEFAULTS : new LinkedHashMap<>();
        Map<String, String> out = new LinkedHashMap<>();
        for (TemplateArea a : t.getAreas()) out.put(a.getAreaKey(), a.getAreaLabel());
        return out;
    }

    public List<CoScholasticResponse.AreaGrade> asList(Map<String, String> areas) {
        return areas.entrySet().stream().map(e -> CoScholasticResponse.AreaGrade.builder().area(e.getKey()).areaLabel(e.getValue()).build()).toList();
    }

    /** The label of a key for display: the term's label, else a readable form of the key (an area removed from the template later) */
    public static String label(Map<String, String> areas, String key) {
        String l = areas.get(key);
        if (l != null) return l;
        String d = DEFAULTS.get(key);
        if (d != null) return d;
        String s = key.toLowerCase().replace('_', ' ');
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
