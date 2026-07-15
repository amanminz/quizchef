package io.quizchef.quiz.domain;

import io.quizchef.common.persistence.AuditableEntity;
import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.quiz.domain.exception.DefaultLocalizationRequiredException;
import io.quizchef.quiz.domain.exception.DuplicateQuizQuestionException;
import io.quizchef.quiz.domain.exception.QuizArchivedException;
import io.quizchef.quiz.domain.exception.QuizContentLockedException;
import io.quizchef.quiz.domain.exception.QuizNotArchivableException;
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
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An authored quiz: settings, lifecycle, localized content, and the
 * ordered composition of reusable questions.
 *
 * <p>The quiz owns only settings, ordering, and its localizations — never
 * Question aggregates. Questions are referenced by id through
 * {@link QuizQuestion} so they stay reusable across quizzes.
 *
 * <p>Content is language neutral: displayable text lives in
 * {@link QuizLocalization}, one per language. The default language is
 * chosen per quiz (a BELC quiz may default to {@code en}, another church's
 * to {@code kn}) and its localization always exists — it is created with
 * the quiz and can never be removed.
 */
@Entity
@Table(name = "quizzes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quiz extends AuditableEntity {

    @Embedded
    @AttributeOverride(name = "identityId",
            column = @Column(name = "owner_identity_id", nullable = false, updatable = false))
    @AttributeOverride(name = "identityType",
            column = @Column(name = "owner_identity_type", nullable = false, updatable = false, length = 20))
    private IdentityReference ownerIdentity;

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "default_language", nullable = false, length = 20))
    private LanguageCode defaultLanguage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuizVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuizState state;

    @Embedded
    private QuizSettings settings;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "quiz_localizations", joinColumns = @JoinColumn(name = "quiz_id"))
    @AttributeOverride(name = "languageCode.value",
            column = @Column(name = "language_code", nullable = false, length = 20))
    private List<QuizLocalization> localizations = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "quiz_questions", joinColumns = @JoinColumn(name = "quiz_id"))
    private List<QuizQuestion> questions = new ArrayList<>();

    private Quiz(UUID id, QuizLocalization defaultContent, IdentityReference ownerIdentity) {
        super(id);
        Objects.requireNonNull(defaultContent, "defaultContent must not be null");
        this.ownerIdentity = Objects.requireNonNull(ownerIdentity, "ownerIdentity must not be null");
        this.defaultLanguage = defaultContent.languageCode();
        this.localizations.add(defaultContent);
        this.visibility = QuizVisibility.PRIVATE;
        this.state = QuizState.DRAFT;
        this.settings = QuizSettings.defaults();
    }

    /**
     * Creates a draft quiz whose default language is the language of the
     * given content.
     */
    public static Quiz create(QuizLocalization defaultContent, IdentityReference ownerIdentity) {
        return new Quiz(UUID.randomUUID(), defaultContent, ownerIdentity);
    }

    /**
     * Adds or replaces the quiz's content for the localization's language —
     * one localization per language, always. Drafts only: published content
     * is what participants signed up for.
     */
    public void localize(QuizLocalization localization) {
        requireDraft();
        Objects.requireNonNull(localization, "localization must not be null");
        localizations.removeIf(existing -> existing.languageCode().equals(localization.languageCode()));
        localizations.add(localization);
    }

    /**
     * Drops a translation. The default language localization is the
     * fallback everything resolves to and can never be removed.
     */
    public void removeLocalization(LanguageCode languageCode) {
        requireDraft();
        Objects.requireNonNull(languageCode, "languageCode must not be null");
        if (languageCode.equals(defaultLanguage)) {
            throw new DefaultLocalizationRequiredException(defaultLanguage);
        }
        boolean removed = localizations.removeIf(existing -> existing.languageCode().equals(languageCode));
        if (!removed) {
            throw new IllegalArgumentException(
                    "Quiz is not localized in %s".formatted(languageCode.value()));
        }
    }

    public void changeVisibility(QuizVisibility visibility) {
        requireModifiable();
        this.visibility = Objects.requireNonNull(visibility, "visibility must not be null");
    }

    public void updateSettings(QuizSettings settings) {
        requireDraft();
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

    /**
     * Retires a published quiz. Drafts cannot be archived — they are edited
     * or abandoned; archiving exists to take a live quiz out of circulation
     * while retaining it (no deletion).
     */
    public void archive() {
        requireModifiable();
        if (state != QuizState.PUBLISHED) {
            throw new QuizNotArchivableException();
        }
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

    public List<QuizLocalization> localizations() {
        return localizations.stream()
                .sorted(Comparator.comparing(localization -> localization.languageCode().value()))
                .toList();
    }

    public Optional<QuizLocalization> localization(LanguageCode languageCode) {
        return localizations.stream()
                .filter(existing -> existing.languageCode().equals(languageCode))
                .findFirst();
    }

    /**
     * The localization for the default language — guaranteed to exist.
     */
    public QuizLocalization defaultLocalization() {
        return localization(defaultLanguage).orElseThrow();
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

    /**
     * Content and settings change only while DRAFT; a published quiz may
     * change nothing but visibility.
     */
    private void requireDraft() {
        requireModifiable();
        if (state != QuizState.DRAFT) {
            throw new QuizContentLockedException();
        }
    }
}
