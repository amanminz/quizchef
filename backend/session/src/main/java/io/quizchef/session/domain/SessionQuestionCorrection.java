package io.quizchef.session.domain;

import io.quizchef.common.persistence.AuditableEntity;
import io.quizchef.quiz.domain.LanguageCode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
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

/**
 * One session's corrected copy of one question: what the host fixed while
 * the game was running.
 *
 * <p>The point is that a bad question mid-event has to be fixable without
 * rewriting history. A published question is immutable and a session pins
 * the version it executes, so editing the library record would silently
 * change every other quiz using it and would retroactively alter what past
 * sessions asked. The correction therefore belongs to the session: the
 * library row is never touched, and only the one running game plays the
 * fixed wording.
 *
 * <p>It is an <strong>overlay, not a replacement</strong>. A language the
 * host did not correct keeps its authored text, and an option they did not
 * reword keeps its own — so a host who fixes only the English answer key
 * does not blank out the Hindi.
 *
 * <p>What it may change is deliberately bounded: the prompt, the option
 * wording, and which options are correct. It cannot add or drop an option,
 * because the option set is what answers already recorded point at — a
 * changed set would make the cancelled attempt and the replayed one
 * incomparable, and would leave the answer distribution counting options
 * that no longer exist.
 */
@Entity
@Table(name = "session_question_corrections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionQuestionCorrection extends AuditableEntity {

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    /**
     * How many times the host has corrected this question in this session,
     * starting at 1. Not a version of the content (that is {@code version},
     * the optimistic lock) — it is how many times the room was asked to
     * play a fixed copy, which is the number worth reading back.
     */
    @Column(name = "revision", nullable = false)
    private int revision;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "session_question_correction_correct_options",
            joinColumns = @JoinColumn(name = "correction_id"))
    @Column(name = "option_id", nullable = false)
    private Set<UUID> correctOptionIds = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "session_question_correction_prompts",
            joinColumns = @JoinColumn(name = "correction_id"))
    private List<CorrectedPrompt> prompts = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "session_question_correction_option_texts",
            joinColumns = @JoinColumn(name = "correction_id"))
    private List<CorrectedOptionText> optionTexts = new ArrayList<>();

    private SessionQuestionCorrection(UUID id, UUID sessionId, UUID questionId) {
        super(id);
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.questionId = Objects.requireNonNull(questionId, "questionId must not be null");
        this.revision = 0;
    }

    /**
     * The host's first correction of this question in this session.
     *
     * @param authoredOptionIds the question's own option ids — the set a
     *                          correction may reword and mark, never extend
     */
    public static SessionQuestionCorrection first(UUID sessionId, UUID questionId,
                                                  Set<UUID> correctOptionIds,
                                                  List<CorrectedPrompt> prompts,
                                                  List<CorrectedOptionText> optionTexts,
                                                  Set<UUID> authoredOptionIds) {
        SessionQuestionCorrection correction =
                new SessionQuestionCorrection(UUID.randomUUID(), sessionId, questionId);
        correction.reviseTo(correctOptionIds, prompts, optionTexts, authoredOptionIds);
        return correction;
    }

    /**
     * Replaces this correction's content wholesale and counts one more
     * revision. Wholesale rather than merged: the host is looking at the
     * question as it currently plays and submits what it should say, so
     * what they send is the answer, not a patch on top of an earlier fix.
     */
    public void reviseTo(Set<UUID> correctOptionIds, List<CorrectedPrompt> prompts,
                         List<CorrectedOptionText> optionTexts, Set<UUID> authoredOptionIds) {
        Objects.requireNonNull(authoredOptionIds, "authoredOptionIds must not be null");
        Set<UUID> correct = new LinkedHashSet<>(
                Objects.requireNonNull(correctOptionIds, "correctOptionIds must not be null"));
        if (correct.isEmpty()) {
            throw new IllegalArgumentException("a corrected question must have a correct option");
        }
        if (!authoredOptionIds.containsAll(correct)) {
            throw new IllegalArgumentException(
                    "correctOptionIds must be options of question " + questionId);
        }
        List<CorrectedPrompt> newPrompts =
                List.copyOf(Objects.requireNonNull(prompts, "prompts must not be null"));
        if (newPrompts.isEmpty()) {
            throw new IllegalArgumentException("a correction must state the prompt in some language");
        }
        requireDistinct(newPrompts.stream().map(CorrectedPrompt::languageCode).toList(),
                "a correction may state one prompt per language");
        List<CorrectedOptionText> newOptionTexts =
                List.copyOf(Objects.requireNonNull(optionTexts, "optionTexts must not be null"));
        if (!newOptionTexts.stream().map(CorrectedOptionText::optionId).allMatch(authoredOptionIds::contains)) {
            throw new IllegalArgumentException(
                    "option texts must belong to options of question " + questionId);
        }
        requireDistinct(newOptionTexts.stream()
                        .map(text -> List.of(text.languageCode(), text.optionId()))
                        .toList(),
                "a correction may state one text per option per language");

        this.correctOptionIds = new LinkedHashSet<>(correct);
        this.prompts = new ArrayList<>(newPrompts);
        this.optionTexts = new ArrayList<>(newOptionTexts);
        this.revision += 1;
    }

    /** The corrected prompt in this language, or empty to keep the authored one. */
    public Optional<String> promptFor(LanguageCode languageCode) {
        return prompts.stream()
                .filter(prompt -> prompt.languageCode().equals(languageCode))
                .map(CorrectedPrompt::prompt)
                .findFirst();
    }

    /** This option's corrected text in this language, or empty to keep the authored one. */
    public Optional<String> optionTextFor(LanguageCode languageCode, UUID optionId) {
        return optionTexts.stream()
                .filter(text -> text.languageCode().equals(languageCode)
                        && text.optionId().equals(optionId))
                .map(CorrectedOptionText::text)
                .findFirst();
    }

    public Set<UUID> correctOptionIds() {
        return Set.copyOf(correctOptionIds);
    }

    public List<CorrectedPrompt> prompts() {
        return List.copyOf(prompts);
    }

    public List<CorrectedOptionText> optionTexts() {
        return List.copyOf(optionTexts);
    }

    private static void requireDistinct(List<?> keys, String message) {
        if (new HashSet<>(keys).size() != keys.size()) {
            throw new IllegalArgumentException(message);
        }
    }
}
