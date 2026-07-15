package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves V6 turns pre-library questions into owned, lifecycle-managed
 * assets: a database is migrated to V5, seeded with questions belonging to
 * a draft and a published quiz, then migrated to V6 — ownership is
 * inherited from the quiz that uses each question, and questions already
 * live inside a published quiz become PUBLISHED (immutable) rather than
 * editable drafts.
 */
@Testcontainers
class QuestionLibraryMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID DRAFT_QUIZ_ID = UUID.randomUUID();
    private static final UUID PUBLISHED_QUIZ_ID = UUID.randomUUID();
    private static final UUID DRAFT_QUIZ_OWNER = UUID.randomUUID();
    private static final UUID PUBLISHED_QUIZ_OWNER = UUID.randomUUID();
    private static final UUID DRAFT_QUESTION_ID = UUID.randomUUID();
    private static final UUID PUBLISHED_QUESTION_ID = UUID.randomUUID();

    @Test
    void v6InheritsOwnershipFromQuizzesAndPromotesLiveQuestions() throws SQLException {
        migrateTo("5");
        seedPreLibraryContent();

        migrateTo("latest");

        try (Connection connection = connect()) {
            // the draft quiz's question stays editable, owned by that quiz's owner
            assertThat(queryString(connection,
                    "SELECT owner_identity_id FROM quizchef.questions WHERE id = ?", DRAFT_QUESTION_ID))
                    .isEqualTo(DRAFT_QUIZ_OWNER.toString());
            assertThat(queryString(connection,
                    "SELECT owner_identity_type FROM quizchef.questions WHERE id = ?", DRAFT_QUESTION_ID))
                    .isEqualTo("REGISTERED");
            assertThat(queryString(connection,
                    "SELECT state FROM quizchef.questions WHERE id = ?", DRAFT_QUESTION_ID))
                    .isEqualTo("DRAFT");

            // a question already live inside a published quiz must not stay editable
            assertThat(queryString(connection,
                    "SELECT owner_identity_id FROM quizchef.questions WHERE id = ?", PUBLISHED_QUESTION_ID))
                    .isEqualTo(PUBLISHED_QUIZ_OWNER.toString());
            assertThat(queryString(connection,
                    "SELECT state FROM quizchef.questions WHERE id = ?", PUBLISHED_QUESTION_ID))
                    .isEqualTo("PUBLISHED");

            // every question is MANUAL until the AI and import features exist
            assertThat(queryString(connection,
                    "SELECT source FROM quizchef.questions WHERE id = ?", DRAFT_QUESTION_ID))
                    .isEqualTo("MANUAL");

            // the tag tables arrive empty
            assertThat(queryString(connection, "SELECT count(*)::text FROM quizchef.tags"))
                    .isEqualTo("0");
            assertThat(queryString(connection, "SELECT count(*)::text FROM quizchef.question_tags"))
                    .isEqualTo("0");
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

    private static void seedPreLibraryContent() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(quizInsert(DRAFT_QUIZ_ID, DRAFT_QUIZ_OWNER, "DRAFT"));
            statement.executeUpdate(quizInsert(PUBLISHED_QUIZ_ID, PUBLISHED_QUIZ_OWNER, "PUBLISHED"));
            statement.executeUpdate(questionInsert(DRAFT_QUESTION_ID));
            statement.executeUpdate(questionInsert(PUBLISHED_QUESTION_ID));
            statement.executeUpdate("""
                    INSERT INTO quizchef.quiz_questions (quiz_id, question_id, display_order)
                    VALUES ('%s', '%s', 1), ('%s', '%s', 1)
                    """.formatted(DRAFT_QUIZ_ID, DRAFT_QUESTION_ID,
                    PUBLISHED_QUIZ_ID, PUBLISHED_QUESTION_ID));
        }
    }

    private static String quizInsert(UUID quizId, UUID ownerId, String state) {
        return """
                INSERT INTO quizchef.quizzes (id, default_language, owner_identity_id,
                    owner_identity_type, visibility, state, randomize_question_order,
                    randomize_option_order, question_time_limit_seconds,
                    show_leaderboard_after_question, show_explanation_after_question,
                    version, created_at, updated_at)
                VALUES ('%s', 'en', '%s', 'REGISTERED', 'PRIVATE', '%s', false, false, 30,
                    true, true, 0, now(), now())
                """.formatted(quizId, ownerId, state);
    }

    private static String questionInsert(UUID questionId) {
        return """
                INSERT INTO quizchef.questions (id, default_language, question_type, difficulty,
                    version, created_at, updated_at)
                VALUES ('%s', 'en', 'TRUE_FALSE', 'EASY', 0, now(), now())
                """.formatted(questionId);
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
}
