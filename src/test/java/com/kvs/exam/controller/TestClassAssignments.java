package com.kvs.exam.controller;

import com.kvs.core.rbac.ClassAssignments;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RBAC Phase 6: the teachers' sections for the tests. The H2 test database has no academicservice allocations or staff
 * profiles, so tests say which sections each teacher's user id teaches (ms-core's ScopeGuard asks this bean).
 */
@TestConfiguration
public class TestClassAssignments {

    private static final Map<String, Set<UUID>> SECTIONS = new ConcurrentHashMap<>();

    /** Gives the user these sections (replacing any earlier ones) */
    public static void teach(String userId, UUID... sectionIds) {
        SECTIONS.put(userId, Set.of(sectionIds));
    }

    public static void clear() {
        SECTIONS.clear();
    }

    @Bean
    @Primary
    ClassAssignments teachersSections() {
        return (tenantId, userId) -> userId == null ? Set.of() : SECTIONS.getOrDefault(userId, Set.of());
    }
}
