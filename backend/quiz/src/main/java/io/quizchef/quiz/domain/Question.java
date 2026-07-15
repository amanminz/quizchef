package io.quizchef.quiz.domain;

import io.quizchef.common.persistence.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A reusable question: its own aggregate, never owned by a quiz.
 *
 * <p>Questions outlive and cross quizzes — the same question can appear in
 * many quizzes (question bank, PRD roadmap v1.1), so quizzes reference it
 * by id through QuizQuestion. Structural rules follow the
 * {@link QuestionType}: single choice has exactly one correct option,
 * multiple choice at least one, true/false exactly two options with one
 * correct.
 */
@Entity
@Table(name = "questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question extends AuditableEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 4000)
    private String prompt;

    @Column(length = 4000)
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Difficulty difficulty;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "question_options", joinColumns = @JoinColumn(name = "question_id"))
    private List<Option> options = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "question_bible_references", joinColumns = @JoinColumn(name = "question_id"))
    private List<BibleReference> bibleReferences = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "question_media_references", joinColumns = @JoinColumn(name = "question_id"))
    private List<MediaReference> mediaReferences = new ArrayList<>();

    private Question(UUID id, String title, String prompt, String explanation,
                     QuestionType questionType, Difficulty difficulty, List<Option> options) {
        super(id);
        this.title = requireText(title, "title");
        this.prompt = requireText(prompt, "prompt");
        this.explanation = explanation;
        this.questionType = Objects.requireNonNull(questionType, "questionType must not be null");
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty must not be null");
        this.options = new ArrayList<>(validateOptions(questionType, options));
    }

    public static Question create(String title, String prompt, String explanation,
                                  QuestionType questionType, Difficulty difficulty,
                                  List<Option> options) {
        return new Question(UUID.randomUUID(), title, prompt, explanation,
                questionType, difficulty, options);
    }

    public void rename(String title) {
        this.title = requireText(title, "title");
    }

    public void changePrompt(String prompt) {
        this.prompt = requireText(prompt, "prompt");
    }

    public void explainWith(String explanation) {
        this.explanation = explanation;
    }

    /**
     * Swaps the option set atomically; the type's structural rules are
     * re-validated so the aggregate can never hold an invalid combination.
     */
    public void replaceOptions(List<Option> options) {
        List<Option> validated = validateOptions(questionType, options);
        this.options.clear();
        this.options.addAll(validated);
    }

    public void updateBibleReferences(List<BibleReference> references) {
        Objects.requireNonNull(references, "references must not be null");
        this.bibleReferences.clear();
        this.bibleReferences.addAll(references);
    }

    public void updateMediaReferences(List<MediaReference> references) {
        Objects.requireNonNull(references, "references must not be null");
        requireUniqueOrders(references.stream().map(MediaReference::displayOrder).toList(),
                "media reference displayOrder must be unique");
        this.mediaReferences.clear();
        this.mediaReferences.addAll(references);
    }

    public List<Option> options() {
        return options.stream()
                .sorted(Comparator.comparingInt(Option::displayOrder))
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

    private static List<Option> validateOptions(QuestionType type, List<Option> options) {
        Objects.requireNonNull(options, "options must not be null");
        if (options.isEmpty()) {
            throw new IllegalArgumentException("a question needs at least one option");
        }
        requireUniqueOrders(options.stream().map(Option::displayOrder).toList(),
                "option displayOrder must be unique");
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

    private static void requireUniqueOrders(List<Integer> orders, String message) {
        Set<Integer> seen = new HashSet<>();
        for (Integer order : orders) {
            if (!seen.add(order)) {
                throw new IllegalArgumentException(message);
            }
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
