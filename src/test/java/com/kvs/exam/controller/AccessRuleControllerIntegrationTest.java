package com.kvs.exam.controller;

import com.kvs.core.rbac.AccessRuleController;
import com.kvs.core.service.JwtTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.server.PathContainer;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

/**
 * Integration tests for {@link AccessRuleController}: endpoint → allowed roles, read from the running service.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccessRuleControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    private String bearer(String role) {
        return "Bearer " + jwtTokenService.generateToken("rules@kvs.com", Map.of("roles", List.of("ROLE_" + role)));
    }

    @Test
    @DisplayName("GET /api/v1/exam/access-rules - lists each endpoint with the roles its @PreAuthorize allows")
    void listsRules() throws Exception {
        mockMvc.perform(get("/api/v1/exam/access-rules").header("Authorization", bearer("REGISTRAR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThan(15)))
                .andExpect(jsonPath("$.data[?(@.method == 'POST' && @.path == '/api/v1/exams/terms/{examTermId}/publish')].roles[*]",
                        containsInAnyOrder("CLASS_TEACHER", "SCHOOL_PRINCIPAL", "EXAM_CONTROLLER", "SUPER_ADMIN")))
                .andExpect(jsonPath("$.data[?(@.method == 'PUT' && @.path == '/api/v1/exams/terms/{examTermId}/marks')].roles[*]",
                        containsInAnyOrder("SUBJECT_TEACHER", "CLASS_TEACHER", "EXAM_CONTROLLER", "SUPER_ADMIN")));
    }

    @Test
    @DisplayName("GET /api/v1/exam/access-rules - 401 without a token")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/exam/access-rules")).andExpect(status().isUnauthorized());
    }

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("GET /api/v1/exam/access-rules is answered only by the rules endpoint (no resource path shadows it)")
    void rulesPathIsNotShadowedByAResource() {
        PathContainer rules = PathContainer.parsePath("/api/v1/exam/access-rules");
        List<String> shadows = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(e -> e.getKey().getMethodsCondition().getMethods().isEmpty()
                        || e.getKey().getMethodsCondition().getMethods().contains(RequestMethod.GET))
                .filter(e -> !e.getValue().getBeanType().equals(AccessRuleController.class))
                .filter(e -> e.getKey().getPatternValues().stream()
                        .anyMatch(p -> PathPatternParser.defaultInstance.parse(p).matches(rules)))
                .map(e -> e.getKey().getPatternValues() + " -> " + e.getValue())
                .toList();
        assertThat(shadows, empty());
    }
}
