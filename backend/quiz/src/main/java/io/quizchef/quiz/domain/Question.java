package io.quizchef.quiz.domain;

import io.quizchef.common.persistence.AuditableEntity;
import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.quiz.domain.exception.DefaultLocalizationRequiredException;
import io.quizchef.quiz.domain.exception.QuestionArchivedException;
import io.quizchef.quiz.domain.exception.QuestionContentLockedException;
import io.quizchef.quiz.domain.exception.QuestionNotArchivableException;
import io.quizchef.quiz.domain.exception.QuestionNotPublishableException;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

/**
 * A reusable question: its own aggregate, never owned by a quiz.
 *
 * <p>Questions are authored once and cross quizzes — the same question can
 * appear in many quizzes (question bank, PRD roadmap v1.1), so quizzes
 * reference it by id through QuizQuestion. Structural rules follow the
 * {@link QuestionType}: single choice has exactly one correct option,
 * multiple choice at least one, true/false exactly two options with one
 * correct.
 *
 * <p>Lifecycle: DRAFT (fully editable) → PUBLISHED (immutable — quizzes
 * may rely on it — but attachable) → ARCHIVED (unavailable for new
 * quizzes; existing published quizzes continue functioning). Owned by the
 * identity that authored it.
 *
 * <p>Content is language neutral: structure (type, difficulty, options
 * with correctness, references, tags) never varies by language, while
 * displayable text lives in {@link QuestionLocalization} and
 * {@link OptionLocalization}. A language is either fully present —
 * question text plus a text for every option — or absent; partial
 * translations cannot exist. The default language is fixed at creation
 * (the language the question was authored in) and its localization can
 * never be removed.
 */
@Entity
@Table(name = "questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question extends AuditableEntity {

    @Embedded
    @AttributeOverride(name = "identityId",
            column = @Column(name = "owner_identity_id", nullable = false, updatable = false))
    @AttributeOverride(name = "identityType",
            column = @Column(name = "owner_identity_type", nullable = false, updatable = false, length = 20))
    private IdentityReference ownerIdentity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionState state;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private QuestionSource source;

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "default_language", nullable = false, length = 20))
    private LanguageCode defaultLanguage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Difficulty difficulty;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "question_options", joinColumns = @JoinColumn(name = "question_id"))
    private List<Option> options = new ArrayList<>();

    /**
     * {@code @BatchSize} turns per-row lazy loads into batched IN-queries
     * when a page of the library is searched and each row's summary needs
     * its title (default localization) — without it, listing a page of N
     * questions triggers N extra queries.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "question_localizations", joinColumns = @JoinColumn(name = "question_id"))
    @AttributeOverride(name = "languageCode.value",
            column = @Column(name = "language_code", nullable = false, length = 20))
    @BatchSize(size = 50)
    private List<QuestionLocalization> localizations = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "option_localizations", joinColumns = @JoinColumn(name = "question_id"))
    @AttributeOverride(name = "languageCode.value",
            column = @Column(name = "language_code", nullable = false, length = 20))
    private List<OptionLocalization> optionLocalizations = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "question_bible_references", joinColumns = @JoinColumn(name = "question_id"))
    private List<BibleReference> bibleReferences = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "question_media_references", joinColumns = @JoinColumn(name = "question_id"))
    private List<MediaReference> mediaReferences = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "question_tags", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "tag_id", nullable = false)
    @BatchSize(size = 50)
    private Set<UUID> tagIds = new LinkedHashSet<>();

    private Question(UUID id, QuestionLocalization defaultContent, IdentityReference ownerIdentity,
                     QuestionType questionType, Difficulty difficulty, List<Option> options,
                     List<OptionLocalization> defaultOptionTexts) {
        super(id);
        Objects.requireNonNull(defaultContent, "defaultContent must not be null");
        this.ownerIdentity = Objects.requireNonNull(ownerIdentity, "ownerIdentity must not be null");
        this.state = QuestionState.DRAFT;
        this.source = QuestionSource.MANUAL;
        this.defaultLanguage = defaultContent.languageCode();
        this.questionType = Objects.requireNonNull(questionType, "questionType must not be null");
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty must not be null");
        List<Option> validated = validateOptions(questionType, options);
        validateOptionTexts(defaultLanguage, validated, defaultOptionTexts);
        this.options = new ArrayList<>(validated);
        this.localizations.add(defaultContent);
        this.optionLocalizations.addAll(defaultOptionTexts);
    }

    /**
     * Creates a draft question whose default language is the language of
     * the given content; the option texts must cover every option in that
     * same language. The authoring API only creates MANUAL questions — AI
     * and IMPORT sources arrive with their features.
     */
    public static Question create(QuestionLocalization defaultContent, IdentityReference ownerIdentity,
                                  QuestionType questionType, Difficulty difficulty, List<Option> options,
                                  List<OptionLocalization> defaultOptionTexts) {
        return new Question(UUID.randomUUID(), defaultContent, ownerIdentity, questionType,
                difficulty, options, defaultOptionTexts);
    }

    /**
     * Publishing freezes the question: quizzes may rely on it from now on.
     * The localization invariants (default language present, every option
     * localized per stored language) hold by construction.
     */
    public void publish() {
        requireModifiable();
        if (state == QuestionState.PUBLISHED) {
            throw new QuestionNotPublishableException("Question is already published");
        }
        this.state = QuestionState.PUBLISHED;
    }

    /**
     * Retires a published question: unavailable for new quizzes, while
     * existing published quizzes continue functioning. Drafts are edited
     * or abandoned, never archived.
     */
    public void archive() {
        requireModifiable();
        if (state != QuestionState.PUBLISHED) {
            throw new QuestionNotArchivableException();
        }
        this.state = QuestionState.ARCHIVED;
    }

    public void changeDifficulty(Difficulty difficulty) {
        requireDraft();
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty must not be null");
    }

    /**
     * Adds or replaces the question's content for the localization's
     * language. A translation is all or nothing: the option texts must
     * cover every option, in the same language.
     */
    public void localize(QuestionLocalization localization, List<OptionLocalization> optionTexts) {
        requireDraft();
        Objects.requireNonNull(localization, "localization must not be null");
        validateOptionTexts(localization.languageCode(), options, optionTexts);
        removeLanguage(localization.languageCode());
        localizations.add(localization);
        optionLocalizations.addAll(optionTexts);
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
                    "Question is not localized in %s".formatted(languageCode.value()));
        }
        optionLocalizations.removeIf(text -> text.languageCode().equals(languageCode));
    }

    /**
     * Swaps the option set atomically; the type's structural rules are
     * re-validated so the aggregate can never hold an invalid combination.
     *
     * <p>The new default-language texts must be supplied. Other languages
     * survive only if their existing texts still cover every new option
     * (texts of dropped options are pruned); a language left incomplete by
     * the change is removed entirely and must be re-translated.
     */
    public void replaceOptions(List<Option> options, List<OptionLocalization> defaultOptionTexts) {
        requireDraft();
        List<Option> validated = validateOptions(questionType, options);
        validateOptionTexts(defaultLanguage, validated, defaultOptionTexts);

        Set<UUID> newOptionIds = new HashSet<>();
        validated.forEach(option -> newOptionIds.add(option.id()));

        List<OptionLocalization> survivingTexts = new ArrayList<>(defaultOptionTexts);
        for (QuestionLocalization localization : List.copyOf(localizations)) {
            LanguageCode language = localization.languageCode();
            if (language.equals(defaultLanguage)) {
                continue;
            }
            List<OptionLocalization> covering = optionLocalizations.stream()
                    .filter(text -> text.languageCode().equals(language))
                    .filter(text -> newOptionIds.contains(text.optionId()))
                    .toList();
            if (covering.size() == newOptionIds.size()) {
                survivingTexts.addAll(covering);
            } else {
                localizations.remove(localization);
            }
        }

        this.options.clear();
        this.options.addAll(validated);
        this.optionLocalizations.clear();
        this.optionLocalizations.addAll(survivingTexts);
    }

    public void updateBibleReferences(List<BibleReference> references) {
        requireDraft();
        Objects.requireNonNull(references, "references must not be null");
        this.bibleReferences.clear();
        this.bibleReferences.addAll(references);
    }

    public void updateMediaReferences(List<MediaReference> references) {
        requireDraft();
        Objects.requireNonNull(references, "references must not be null");
        requireUniqueOrders(references.stream().map(MediaReference::displayOrder).toList(),
                "media reference displayOrder must be unique");
        this.mediaReferences.clear();
        this.mediaReferences.addAll(references);
    }

    /**
     * Replaces the tag set. Questions hold tag ids only — the Tag
     * aggregate owns names and everything that will grow on them.
     */
    public void updateTags(Set<UUID> tagIds) {
        requireDraft();
        Objects.requireNonNull(tagIds, "tagIds must not be null");
        this.tagIds.clear();
        this.tagIds.addAll(tagIds);
    }

    public boolean isPublished() {
        return state == QuestionState.PUBLISHED;
    }

    public boolean isArchived() {
        return state == QuestionState.ARCHIVED;
    }

    public List<Option> options() {
        return options.stream()
                .sorted(Comparator.comparingInt(Option::displayOrder))
                .toList();
    }

    public List<QuestionLocalization> localizations() {
        return localizations.stream()
                .sorted(Comparator.comparing(localization -> localization.languageCode().value()))
                .toList();
    }

    public Optional<QuestionLocalization> localization(LanguageCode languageCode) {
        return localizations.stream()
                .filter(existing -> existing.languageCode().equals(languageCode))
                .findFirst();
    }

    /**
     * The localization for the default language — guaranteed to exist.
     */
    public QuestionLocalization defaultLocalization() {
        return localization(defaultLanguage).orElseThrow();
    }

    /**
     * The option texts of one language, in option display order.
     */
    public List<OptionLocalization> optionLocalizations(LanguageCode languageCode) {
        List<Option> ordered = options();
        return optionLocalizations.stream()
                .filter(text -> text.languageCode().equals(languageCode))
                .sorted(Comparator.comparingInt(text -> displayOrderOf(text.optionId(), ordered)))
                .toList();
    }

    public List<BibleReference> bibleReferences() {
        return List.copyOf(bibleReferences);
    }

    public List<MediaReference> mediaReferences() {
        return mediaReferences.stream()
                .sorted(Comparator.comparingInt(MediaReference::displayOrder))
                .toList();
    }

    public Set<UUID> tagIds() {
        return Set.copyOf(tagIds);
    }

    private static int displayOrderOf(UUID optionId, List<Option> ordered) {
        for (Option option : ordered) {
            if (option.id().equals(optionId)) {
                return option.displayOrder();
            }
        }
        return Integer.MAX_VALUE;
    }

    private static List<Option> validateOptions(QuestionType type, List<Option> options) {
        Objects.requireNonNull(options, "options must not be null");
        if (options.isEmpty()) {
            throw new IllegalArgumentException("a question needs at least one option");
        }
        requireUniqueOrders(options.stream().map(Option::displayOrder).toList(),
                "option displayOrder must be unique");
        Set<UUID> ids = new HashSet<>();
        for (Option option : options) {
            if (!ids.add(option.id())) {
                throw new IllegalArgumentException("option ids must be unique");
            }
        }
        long correctCount = options.stream().filter(Option::correct).count();
        switch (type) {
            case SINGLE_CHOICE -> {
                if (correctCount != 1) {
                    throw new IllegalArgumentException(
                            "a single-choice question needs exactly one correct option");
                }
            }
            case MULTIPLE_CHOICE -> {
                if (correctCount < 1) {
                    throw new IllegalArgumentException(
                            "a multiple-choice question needs at least one correct option");
                }
            }
            case TRUE_FALSE -> {
                if (options.size() != 2) {
                    throw new IllegalArgumentException(
                            "a true/false question needs exactly two options");
                }
                if (correctCount != 1) {
                    throw new IllegalArgumentException(
                            "a true/false question needs exactly one correct option");
                }
            }
        }
        return List.copyOf(options);
    }

    /**
     * A translation must be whole: one text per option, no strays, all in
     * the language being localized.
     */
    private static void validateOptionTexts(LanguageCode language, List<Option> options,
                                            List<OptionLocalization> optionTexts) {
        Objects.requireNonNull(optionTexts, "optionTexts must not be null");
        Set<UUID> optionIds = new HashSet<>();
        options.forEach(option -> optionIds.add(option.id()));
        Set<UUID> localizedIds = new HashSet<>();
        for (OptionLocalization text : optionTexts) {
            if (!text.languageCode().equals(language)) {
                throw new IllegalArgumentException(
                        "option text for %s does not match the localization language %s"
                                .formatted(text.languageCode().value(), language.value()));
            }
            if (!optionIds.contains(text.optionId())) {
                throw new IllegalArgumentException(
                        "option text references unknown option %s".formatted(text.optionId()));
            }
            if (!localizedIds.add(text.optionId())) {
                throw new IllegalArgumentException(
                        "option %s is localized twice".formatted(text.optionId()));
            }
        }
        if (localizedIds.size() != optionIds.size()) {
            throw new IllegalArgumentException(
                    "every option must be localized: expected %d option texts, got %d"
                            .formatted(optionIds.size(), localizedIds.size()));
        }
    }

    private static void requireUniqueOrders(List<Integer> orders, String message) {
        Set<Integer> seen = new HashSet<>();
        for (Integer order : orders) {
            if (!seen.add(order)) {
                throw new IllegalArgumentException(message);
            }
        }
    }

    private void removeLanguage(LanguageCode languageCode) {
        localizations.removeIf(existing -> existing.languageCode().equals(languageCode));
        optionLocalizations.removeIf(text -> text.languageCode().equals(languageCode));
    }

    private void requireModifiable() {
        if (state == QuestionState.ARCHIVED) {
            throw new QuestionArchivedException();
        }
    }

    /**
     * Content changes only while DRAFT; a published question is immutable.
     */
    private void requireDraft() {
        requireModifiable();
        if (state != QuestionState.DRAFT) {
            throw new QuestionContentLockedException();
        }
    }
}
