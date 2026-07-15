package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves V4 preserves pre-i18n content: a database is migrated to V3,
 * seeded with a quiz, a question, and options authored before content
 * internationalization existed, then migrated to V4 — every title, prompt,
 * and option text must reappear as the 'en' localization of the same
 * aggregate, with the aggregate ids unchanged.
 */
@Testcontainers
class ContentI18nMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID QUIZ_ID = UUID.randomUUID();
    private static final UUID QUESTION_ID = UUID.randomUUID();
    private static final UUID OPTION_TRUE_ID = UUID.randomUUID();
    private static final UUID OPTION_FALSE_ID = UUID.randomUUID();

    @Test
    void v4MovesExistingContentIntoLocalizationTables() throws SQLException {
        migrateTo("3");
        seedPreI18nContent();

        migrateTo("latest");

        try (Connection connection = connect()) {
            assertThat(queryString(connection,
                    "SELECT default_language FROM quizchef.quizzes WHERE id = ?", QUIZ_ID))
                    .isEqualTo("en");
            assertThat(queryString(connection,
                    "SELECT title FROM quizchef.quiz_localizations"
                            + " WHERE quiz_id = ? AND language_code = 'en'", QUIZ_ID))
                    .isEqualTo("BELC Bible Quiz");
            assertThat(queryString(connection,
                    "SELECT description FROM quizchef.quiz_localizations"
                            + " WHERE quiz_id = ? AND language_code = 'en'", QUIZ_ID))
                    .isEqualTo("Weekly quiz");

            assertThat(queryString(connection,
                    "SELECT default_language FROM quizchef.questions WHERE id = ?", QUESTION_ID))
                    .isEqualTo("en");
            assertThat(queryString(connection,
                    "SELECT prompt FROM quizchef.question_localizations"
                            + " WHERE question_id = ? AND language_code = 'en'", QUESTION_ID))
                    .isEqualTo("Jonah was swallowed by a great fish.");
            assertThat(queryString(connection,
                    "SELECT explanation FROM quizchef.question_localizations"
                            + " WHERE question_id = ? AND language_code = 'en'", QUESTION_ID))
                    .isEqualTo("See Jonah 1:17.");

            assertThat(queryString(connection,
                    "SELECT text FROM quizchef.option_localizations"
                            + " WHERE question_id = ? AND option_id = ? AND language_code = 'en'",
                    QUESTION_ID, OPTION_TRUE_ID))
                    .isEqualTo("True");
            assertThat(queryString(connection,
                    "SELECT text FROM quizchef.option_localizations"
                            + " WHERE question_id = ? AND option_id = ? AND language_code = 'en'",
                    QUESTION_ID, OPTION_FALSE_ID))
                    .isEqualTo("False");

            // the language-neutral columns are gone from the aggregate tables
            assertThat(columnsOf(connection, "quizzes"))
                    .contains("default_language")
                    .doesNotContain("title", "description");
            assertThat(columnsOf(connection, "questions"))
                    .contains("default_language")
                    .doesNotContain("title", "prompt", "explanation");
            assertThat(columnsOf(connection, "question_options"))
                    .contains("correct", "display_order")
                    .doesNotContain("text");
        }
    }

    private static void migrateTo(String version) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .schemas("quizchef")
                .defaultSchema("quizchef")
                .target(version)
                .load()
                .migrate();
    }

    private static void seedPreI18nContent() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO quizchef.quizzes (id, title, description, owner_identity_id,
                        owner_identity_type, visibility, state, randomize_question_order,
                        randomize_option_order, question_time_limit_seconds,
                        show_leaderboard_after_question, show_explanation_after_question,
                        created_at, updated_at)
                    VALUES ('%s', 'BELC Bible Quiz', 'Weekly quiz', '%s', 'REGISTERED',
                        'PRIVATE', 'DRAFT', false, false, 30, true, true, now(), now())
                    """.formatted(QUIZ_ID, UUID.randomUUID()));
            statement.executeUpdate("""
                    INSERT INTO quizchef.questions (id, title, prompt, explanation,
                        question_type, difficulty, created_at, updated_at)
                    VALUES ('%s', 'Jonah', 'Jonah was swallowed by a great fish.',
                        'See Jonah 1:17.', 'TRUE_FALSE', 'EASY', now(), now())
                    """.formatted(QUESTION_ID));
            statement.executeUpdate("""
                    INSERT INTO quizchef.question_options (question_id, id, text, correct, display_order)
                    VALUES ('%s', '%s', 'True', true, 1), ('%s', '%s', 'False', false, 2)
                    """.formatted(QUESTION_ID, OPTION_TRUE_ID, QUESTION_ID, OPTION_FALSE_ID));
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static String queryString(Connection connection, String sql, UUID... ids)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < ids.length; i++) {
                statement.setObject(i + 1, ids[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).as("expected a row for: " + sql).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private static List<String> columnsOf(Connection connection, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT column_name FROM information_schema.columns"
                        + " WHERE table_schema = 'quizchef' AND table_name = ?")) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(resultSet.getString(1));
                }
            }
        }
        return columns;
    }
}
