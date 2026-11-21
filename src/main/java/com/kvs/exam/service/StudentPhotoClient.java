package com.kvs.exam.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * A student's photo for printed documents (admit cards), from studentservice
 * ({@code GET /api/v1/students/{id}/photo/file?size=}), read with the signed-in user's token. Null when there is no
 * token, no photo, or the caller may not see it; the document then shows an empty photo box.
 */
@Component
@Slf4j
public class StudentPhotoClient {

    /** Pixels on the long side: sharp enough for a 3.5 x 4.5 cm print */
    static final int PRINT_SIZE = 360;

    private final RestClient http;
    private final String studentUrl;

    public StudentPhotoClient(@Value("${kvs.integrations.student-url:http://localhost:8084/studentservice}") String studentUrl) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3_000);
        f.setReadTimeout(10_000);
        this.http = RestClient.builder().requestFactory(f).build();
        this.studentUrl = studentUrl.endsWith("/") ? studentUrl.substring(0, studentUrl.length() - 1) : studentUrl;
    }

    public byte[] photo(UUID studentId) {
        String bearer = bearer();
        if (bearer == null) return null;
        try {
            return http.get().uri(studentUrl + "/api/v1/students/{id}/photo/file?size={size}", studentId, PRINT_SIZE)
                    .header(HttpHeaders.AUTHORIZATION, bearer).retrieve().body(byte[].class);
        } catch (Exception e) {
            log.debug("Photo of student {} not read: {}", studentId, e.getMessage());
            return null;
        }
    }

    private static String bearer() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
            String h = a.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            return h != null && h.startsWith("Bearer ") ? h : null;
        }
        return null;
    }
}
