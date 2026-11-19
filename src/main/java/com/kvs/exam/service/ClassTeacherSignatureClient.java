package com.kvs.exam.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kvs.core.school.SchoolProfile;
import com.kvs.exam.entity.Section;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * A section's class teacher as a signatory on report cards and admit cards: the name from the section, the scanned
 * signature from hrservice ({@code POST /internal/v1/hr/staff-signatures}, shared X-Internal-Key). Without a class
 * teacher the card keeps a plain "Class teacher" line; without an uploaded signature it prints the teacher's name
 * under a blank line; when hrservice cannot be reached, the same.
 */
@Component
@Slf4j
public class ClassTeacherSignatureClient {

    static final String TITLE = "Class teacher";

    private final RestClient http;
    private final ObjectMapper objectMapper;
    private final String hrUrl;
    private final String internalKey;
    private final String serviceName;

    public ClassTeacherSignatureClient(ObjectMapper objectMapper,
                                       @Value("${kvs.integrations.hr-url:http://localhost:8088/hrservice}") String hrUrl,
                                       @Value("${kvs.internal.key:schoolerp-dev-internal-key}") String internalKey,
                                       @Value("${spring.application.name:examservice}") String serviceName) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3_000);
        f.setReadTimeout(10_000);
        this.http = RestClient.builder().requestFactory(f).build();
        this.objectMapper = objectMapper;
        this.hrUrl = hrUrl.endsWith("/") ? hrUrl.substring(0, hrUrl.length() - 1) : hrUrl;
        this.internalKey = internalKey;
        this.serviceName = serviceName;
    }

    /** The class teacher of the section as a signatory, or null when the section has none */
    public SchoolProfile.Signatory forSection(Section section, String tenantId) {
        if (section == null || section.getClassTeacherId() == null) return null;
        String name = section.getClassTeacherName() == null ? "" : section.getClassTeacherName().trim();
        byte[] image = null;
        try {
            JsonNode r = objectMapper.readTree(http.post().uri(hrUrl + "/internal/v1/hr/staff-signatures")
                    .header("X-Internal-Key", internalKey).header("X-Internal-Service", serviceName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("tenantId", tenantId, "staffIds", List.of(section.getClassTeacherId())))
                    .retrieve().body(String.class));
            for (JsonNode s : r) {
                if (section.getClassTeacherId().toString().equals(s.path("staffId").asText())) {
                    if (name.isEmpty()) name = s.path("name").asText("");
                    image = Base64.getDecoder().decode(s.path("signature").asText(""));
                }
            }
        } catch (Exception e) {
            log.warn("Class teacher signature of section {} not read: {}", section.getSectionId(), e.getMessage());
        }
        return new SchoolProfile.Signatory(name, TITLE, image == null || image.length == 0 ? null : image);
    }
}
