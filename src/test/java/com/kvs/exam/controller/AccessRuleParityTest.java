package com.kvs.exam.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kvs.core.service.JwtTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * RBAC migration parity: after the switch from role checks to permission checks, every endpoint must be open to
 * exactly the roles it was open to before (rbac/baseline-exam.json, captured from the running service on
 * 2026-11-08), after the accepted role-code decisions (rbac/role-code-decisions.json). Roles come from the shared
 * access-rules endpoint, which expands each endpoint's permission into the roles holding it (catalogue defaults here).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccessRuleParityTest {

    private static final String RULES_PATH = "/api/v1/exam/access-rules";

    /** Intended differences, reviewed: "METHOD path" → reason. Keep empty unless a change is deliberate. */
    private static final Map<String, String> ALLOWED_DIFFERENCES = Map.of(
            "PUT /api/v1/exams/terms/{examTermId}/date-sheet", "New (18 Nov 2026): date sheet, exam.templates.manage like the subject set-up",
            "GET /api/v1/exams/terms/{examTermId}/admit-cards", "New (18 Nov 2026): admit cards of a section, exam.terms.view like the broadsheet",
            "GET /api/v1/exams/students/{studentId}/admit-card", "New (18 Nov 2026): one student's admit card, exam.students.view like the progress card",
            "GET /api/v1/exams/students/{studentId}/date-sheet", "New (18 Nov 2026): one student's date sheet for the parent app, exam.students.view",
            "GET /api/v1/exams/terms/{examTermId}/report-cards", "New (18 Nov 2026): report cards of a section, exam.terms.view like the broadsheet",
            "GET /api/v1/exams/students/{studentId}/progress-card/pdf", "New (18 Nov 2026): one student's report card PDF, exam.students.view like the progress card");

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private ObjectMapper mapper;

    @Test
    @DisplayName("Every endpoint allows exactly the baseline roles (after role-code decisions)")
    void permissionsReproduceTheBaseline() throws Exception {
        String token = jwtTokenService.generateToken("parity@kvs.com", Map.of("roles", List.of("ROLE_SUPER_ADMIN")));
        String body = mockMvc.perform(get(RULES_PATH).header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        Map<String, Set<String>> now = new TreeMap<>();
        for (JsonNode r : mapper.readTree(body).path("data")) {
            now.put(r.path("method").asText() + " " + r.path("path").asText(), strings(r.path("roles")));
        }

        JsonNode decisions = read("rbac/role-code-decisions.json");
        Set<String> drop = new TreeSet<>();
        decisions.path("drop").fieldNames().forEachRemaining(drop::add);
        Map<String, Set<String>> map = new HashMap<>();
        decisions.path("map").fields().forEachRemaining(e -> map.put(e.getKey(), strings(e.getValue())));

        Map<String, Set<String>> expected = new TreeMap<>();
        for (JsonNode r : read("rbac/baseline-exam.json").path("rules")) {
            String key = r.path("method").asText() + " " + r.path("path").asText();
            if (key.endsWith(" " + RULES_PATH)) continue; // the rules endpoint itself now lives in ms-core
            Set<String> roles = new TreeSet<>();
            for (String code : strings(r.path("roles"))) {
                if (drop.contains(code)) continue;
                roles.addAll(map.getOrDefault(code, Set.of(code)));
            }
            expected.put(key, roles);
        }

        List<String> differences = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : expected.entrySet()) {
            Set<String> actual = now.get(e.getKey());
            if (ALLOWED_DIFFERENCES.containsKey(e.getKey())) continue;
            if (actual == null) differences.add("missing endpoint " + e.getKey());
            else if (!actual.equals(e.getValue())) differences.add(e.getKey() + ": expected " + e.getValue() + " but is " + actual);
        }
        for (String key : now.keySet()) {
            if (!expected.containsKey(key) && !ALLOWED_DIFFERENCES.containsKey(key)) differences.add("new endpoint not in baseline " + key);
        }
        assertThat(differences).as("access differences from the baseline").isEmpty();
        assertThat(expected).hasSizeGreaterThan(25);
    }

    private JsonNode read(String path) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return mapper.readTree(in);
        }
    }

    private static Set<String> strings(JsonNode array) {
        Set<String> out = new TreeSet<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }
}
