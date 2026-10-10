package com.shashwat.examplatformbackend.exam;

import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class ExamSchemaMaintenance implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public ExamSchemaMaintenance(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        ensureExamCaptureProtectionColumn();
        ensureExamNotificationPasswordColumn();
        refreshExamCandidateFieldConstraint();
    }

    private void ensureExamCaptureProtectionColumn() {
        if (!tableExists("exams") || columnExists("exams", "desktop_capture_protection_enabled")) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE exams ADD COLUMN desktop_capture_protection_enabled BOOLEAN DEFAULT TRUE");
        jdbcTemplate.execute("UPDATE exams SET desktop_capture_protection_enabled = TRUE WHERE desktop_capture_protection_enabled IS NULL");
    }

    private void ensureExamNotificationPasswordColumn() {
        if (!tableExists("exams") || columnExists("exams", "notification_sender_app_password")) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE exams ADD COLUMN notification_sender_app_password VARCHAR(300)");
    }

    private void refreshExamCandidateFieldConstraint() {
        if (!tableExists("exam_candidate_fields")) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE exam_candidate_fields DROP CONSTRAINT IF EXISTS exam_candidate_fields_field_name_check");
        String allowedValues = Arrays.stream(CandidateField.values())
                .map(CandidateField::name)
                .map(value -> "'" + value.replace("'", "''") + "'")
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute(
                "ALTER TABLE exam_candidate_fields " +
                        "ADD CONSTRAINT exam_candidate_fields_field_name_check " +
                        "CHECK (field_name IN (" + allowedValues + "))"
        );
    }

    private boolean tableExists(String tableName) {
        Integer exists = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = ?
                """,
                Integer.class,
                tableName
        );
        return exists != null && exists > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer exists = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = ?
                """,
                Integer.class,
                tableName,
                columnName
        );
        return exists != null && exists > 0;
    }
}
