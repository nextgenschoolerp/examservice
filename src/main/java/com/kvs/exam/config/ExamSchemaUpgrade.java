package com.kvs.exam.config;

import com.kvs.core.tenancy.TenantJobs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Schema changes Hibernate's ddl-auto "update" does not make. Co-scholastic areas used to be an enum, for which Hibernate added a
 * CHECK constraint limiting co_scholastic_grades.area to the five CBSE areas; areas now come from the term's assessment template,
 * so the constraint is dropped. Safe to run on every start.
 */
@Slf4j
@Component
public class ExamSchemaUpgrade implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final TenantJobs tenantJobs;

    public ExamSchemaUpgrade(JdbcTemplate jdbc, TenantJobs tenantJobs) {
        this.jdbc = jdbc;
        this.tenantJobs = tenantJobs;
    }

    /** co_scholastic_grades is school data: runs in each school's schema (once, for all, when schema routing is off). */
    @Override
    public void run(ApplicationArguments args) {
        tenantJobs.forEachSchool("co_scholastic_grades constraint upgrade", tenantId -> {
            try {
                jdbc.execute("ALTER TABLE IF EXISTS co_scholastic_grades DROP CONSTRAINT IF EXISTS co_scholastic_grades_area_check");
            } catch (Exception e) {
                log.warn("Could not drop the co_scholastic_grades area check constraint{}: {}",
                        tenantId == null ? "" : " (school " + tenantId + ")", e.getMessage());
            }
        });
    }
}
