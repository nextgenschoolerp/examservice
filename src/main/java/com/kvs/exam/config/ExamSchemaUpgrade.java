package com.kvs.exam.config;

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

    public ExamSchemaUpgrade(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbc.execute("ALTER TABLE IF EXISTS co_scholastic_grades DROP CONSTRAINT IF EXISTS co_scholastic_grades_area_check");
        } catch (Exception e) {
            log.warn("Could not drop the co_scholastic_grades area check constraint: {}", e.getMessage());
        }
    }
}
