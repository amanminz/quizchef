package io.quizchef.quiz.domain;

import io.quizchef.common.persistence.AuditableEntity;
import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.quiz.domain.exception.DuplicateQuizQuestionException;
import io.quizchef.quiz.domain.exception.QuizArchivedException;
import io.quizchef.quiz.domain.exception.QuizNotPublishableException;
import io.quizchef.quiz.domain.exception.QuizQuestionsLockedException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An authored quiz: metadata, settings, lifecycle, and the ordered
 * composition of reusable questions.
 *
 * <p>The quiz owns only metadata and ordering — never Question aggregates.
 * Questions are referenced by id through {@link QuizQuestion} so they stay
 * reusable across quizzes.
 */
@Entity
@Table(name = "quizzes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quiz extends AuditableEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Embedded
    @AttributeOverride(name = "identityId",
            column = @Column(name = "owner_identity_id", nullable = false, updatable = false))
    @AttributeOverride(name = "identityType",
            column = @Column(name = "owner_identity_type", nullable = false, updatable = false, length = 20))
    private IdentityReference ownerIdentity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuizVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuizState state;

    @Embedded
    private QuizSettings settings;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "quiz_questions", joinColumns = @JoinColumn(name = "quiz_id"))
    private List<QuizQuestion> questions = new ArrayList<>();

    private Quiz(UUID id, String title, String description, IdentityReference ownerIdentity) {
        super(id);
        this.title = requireTitle(title);
        this.description = description;
        this.ownerIdentity = Objects.requireNonNull(ownerIdentity, "ownerIdentity must not be null");
        this.visibility = QuizVisibility.PRIVATE;
        this.state = QuizState.DRAFT;
        this.settings = QuizSettings.defaults();
    }

    public static Quiz create(String title, String description, IdentityReference ownerIdentity) {
        return new Quiz(UUID.randomUUID(), title, description, ownerIdentity);
    }

    public void rename(String title) {
        requireModifiable();
        this.title = requireTitle(title);
    }

    public void describe(String description) {
        requireModifiable();
        this.description = description;
    }

    public void changeVisibility(QuizVisibility visibility) {
        requireModifiable();
        this.visibility = Objects.requireNonNull(visibility, "visibility must not be null");
    }

    public void updateSettings(QuizSettings settings) {
        requireModifiable();
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    /**
     * Appends the question at the next position. Allowed while DRAFT or
     * PUBLISHED — published quizzes may gain questions, never lose them.
     */
    public void addQuestion(UUID questionId) {
        requireModifiable();
        Objects.requireNonNull(questionId, "questionId must not be null");
        if (containsQuestion(questionId)) {
            throw new DuplicateQuizQuestionException(questionId);
        }
        questions.add(new QuizQuestion(questionId, nextDisplayOrder()));
    }

    public void removeQuestion(UUID questionId) {
        requireModifiable();
        if (state == QuizState.PUBLISHED) {
            throw new QuizQuestionsLockedException();
        }
        boolean removed = questions.removeIf(question -> question.questionId().equals(questionId));
        if (!removed) {
            throw new IllegalArgumentException("Question %s is not part of this quiz".formatted(questionId));
        }
    }

    public void publish() {
        requireModifiable();
        if (state == QuizState.PUBLISHED) {
            throw new QuizNotPublishableException("Quiz is already published");
        }
        if (questions.isEmpty()) {
            throw new QuizNotPublishableException("A quiz needs at least one question to be published");
        }
        this.state = QuizState.PUBLISHED;
    }

    public void archive() {
        requireModifiable();
        this.state = QuizState.ARCHIVED;
    }

    public boolean containsQuestion(UUID questionId) {
        return questions.stream().anyMatch(question -> question.questionId().equals(questionId));
    }

    /**
     * The composition in display order.
     */
    public List<QuizQuestion> questions() {
        return questions.stream()
                .sorted(Comparator.comparingInt(QuizQuestion::displayOrder))
                .toList();
    }

    public boolean isPublished() {
        return state == QuizState.PUBLISHED;
    }

    public boolean isArchived() {
        return state == QuizState.ARCHIVED;
    }

    private int nextDisplayOrder() {
        return questions.stream().mapToInt(QuizQuestion::displayOrder).max().orElse(0) + 1;
    }

    private void requireModifiable() {
        if (state == QuizState.ARCHIVED) {
            throw new QuizArchivedException();
        }
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        return title.strip();
    }
}
