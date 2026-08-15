package io.quizchef.session.application;

import io.quizchef.quiz.application.GameplayQuestionContentQuery;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuestionContentView;
import io.quizchef.quiz.application.PlayableQuestionContentView.PlayableLocalizationView;
import io.quizchef.quiz.application.PlayableQuestionContentView.PlayableOptionTextView;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.application.PlayableQuizView.PlayableQuestion;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionQuestionCorrection;
import io.quizchef.session.infrastructure.persistence.SessionQuestionCorrectionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The quiz as one session is actually playing it.
 *
 * <p>A session does not simply execute its published quiz. It may have had
 * its order shuffled, a question corrected, or a question pulled — all of
 * them session-scoped, none of them visible in the published content. This
 * is the single place those differences are applied, so that everything
 * downstream (progression, numbering, scoring, the reveal, the
 * distribution, the results counts) reads one consistent sequence and the
 * engine cannot end up disagreeing with itself about what question five is.
 *
 * <p>It returns the quiz module's own view types deliberately. Every caller
 * already speaks {@link PlayableQuizView} and {@link
 * PlayableQuestionContentView}; making the effective quiz a different shape
 * would have meant touching each of them twice, and would have invited a
 * caller to reach past this service for "the real" quiz. There is no real
 * quiz for a running session — there is only what it is playing.
 *
 * <p>Corrections are an <strong>overlay</strong>: a language the host did
 * not correct keeps its authored text and an option they did not reword
 * keeps its own, so fixing the English answer key never blanks the Hindi.
 * Removals are a <strong>filter</strong>: a removed question is simply not
 * in the sequence, which is what makes numbering close up with no gap and
 * no arithmetic anywhere else.
 */
@Service
public class SessionQuizQuery {

    private final GameplayQuizQuery gameplayQuizQuery;
    private final GameplayQuestionContentQuery contentQuery;
    private final SessionQuestionCorrectionRepository correctionRepository;

    public SessionQuizQuery(GameplayQuizQuery gameplayQuizQuery,
                            GameplayQuestionContentQuery contentQuery,
                            SessionQuestionCorrectionRepository correctionRepository) {
        this.gameplayQuizQuery = gameplayQuizQuery;
        this.contentQuery = contentQuery;
        this.correctionRepository = correctionRepository;
    }

    /**
     * The sequence this session plays, in authored order, with removed
     * questions absent and corrected answer keys applied.
     *
     * <p>Still authored order, not session order — {@link
     * QuestionProgression} remains the one authority on ordering and reads
     * the session's own order on top of this. Splitting them that way keeps
     * "which questions" and "in what sequence" answerable separately, which
     * is what lets the shuffle command validate a permutation against the
     * quiz's full set while play still skips what the host removed.
     */
    @Transactional(readOnly = true)
    public PlayableQuizView effectiveQuiz(Session session) {
        PlayableQuizView authored = gameplayQuizQuery.load(session.getPublishedQuizVersionId());
        Set<UUID> removed = session.removedQuestionIds();
        Map<UUID, SessionQuestionCorrection> corrections = correctionsFor(session);
        if (removed.isEmpty() && corrections.isEmpty()) {
            return authored;
        }
        List<PlayableQuestion> effective = authored.questions().stream()
                .filter(question -> !removed.contains(question.questionId()))
                .map(question -> applyCorrection(question, corrections.get(question.questionId())))
                .toList();
        return new PlayableQuizView(authored.questionTimeLimitSeconds(), effective);
    }

    /**
     * The same, but keeping the questions the host removed.
     *
     * <p>For the host's own view of the session, which shows a removed
     * question struck through where it used to be rather than silently
     * dropping it — the host needs to see what they pulled, even though
     * nothing in gameplay does.
     */
    @Transactional(readOnly = true)
    public PlayableQuizView quizIncludingRemoved(Session session) {
        PlayableQuizView authored = gameplayQuizQuery.load(session.getPublishedQuizVersionId());
        Map<UUID, SessionQuestionCorrection> corrections = correctionsFor(session);
        if (corrections.isEmpty()) {
            return authored;
        }
        return new PlayableQuizView(authored.questionTimeLimitSeconds(),
                authored.questions().stream()
                        .map(question -> applyCorrection(question, corrections.get(question.questionId())))
                        .toList());
    }

    /**
     * One question's participant-safe content as this session shows it —
     * the authored content with the host's corrected wording overlaid.
     *
     * <p>Correctness is not here and cannot be: the view has no field for
     * it. The corrected answer key travels through {@link #effectiveQuiz}
     * instead, which is the engine's scoring boundary and never reaches a
     * participant device.
     */
    @Transactional(readOnly = true)
    public PlayableQuestionContentView effectiveContent(Session session, UUID questionId) {
        PlayableQuestionContentView authored = contentQuery.content(questionId);
        return correctionRepository
                .findBySessionIdAndQuestionId(session.getId(), questionId)
                .map(correction -> overlay(authored, correction))
                .orElse(authored);
    }

    /**
     * The question's authored option ids — the set a correction may reword
     * and mark correct, and may not extend. Read from the published
     * content rather than from any earlier correction, so a chain of
     * corrections can never drift away from the options participants'
     * answers actually point at.
     */
    @Transactional(readOnly = true)
    public Set<UUID> authoredOptionIds(Session session, UUID questionId) {
        return gameplayQuizQuery.load(session.getPublishedQuizVersionId()).questions().stream()
                .filter(question -> question.questionId().equals(questionId))
                .findFirst()
                .map(PlayableQuestion::allOptionIds)
                .orElse(Set.of());
    }

    /** This session's corrections, by question id. */
    @Transactional(readOnly = true)
    public Map<UUID, SessionQuestionCorrection> correctionsFor(Session session) {
        return correctionRepository.findBySessionId(session.getId()).stream()
                .collect(Collectors.toMap(SessionQuestionCorrection::getQuestionId,
                        Function.identity(), (first, second) -> first, LinkedHashMap::new));
    }

    private static PlayableQuestion applyCorrection(PlayableQuestion question,
                                                    SessionQuestionCorrection correction) {
        if (correction == null) {
            return question;
        }
        return new PlayableQuestion(question.questionId(), question.difficulty(),
                correction.correctOptionIds(), question.allOptionIds());
    }

    private static PlayableQuestionContentView overlay(PlayableQuestionContentView authored,
                                                       SessionQuestionCorrection correction) {
        Map<String, PlayableLocalizationView> byLanguage = authored.localizations().stream()
                .collect(Collectors.toMap(PlayableLocalizationView::languageCode,
                        Function.identity(), (first, second) -> first, LinkedHashMap::new));

        // Languages the host corrected but the question was never authored
        // in still get shown. Dropping them would silently discard part of
        // a correction the host believes they made.
        Set<String> correctedLanguages = new LinkedHashSet<>();
        correction.prompts().forEach(prompt -> correctedLanguages.add(prompt.languageCode().value()));
        correction.optionTexts().forEach(text -> correctedLanguages.add(text.languageCode().value()));

        List<PlayableLocalizationView> merged = new ArrayList<>(byLanguage.size());
        byLanguage.forEach((language, localization) ->
                merged.add(corrected(localization, correction)));
        correctedLanguages.stream()
                .filter(language -> !byLanguage.containsKey(language))
                .map(language -> corrected(
                        new PlayableLocalizationView(language, null, null, List.of()), correction))
                .filter(localization -> localization.prompt() != null)
                .forEach(merged::add);

        return new PlayableQuestionContentView(authored.questionId(), authored.questionType(),
                authored.defaultLanguage(), authored.options(), List.copyOf(merged));
    }

    private static PlayableLocalizationView corrected(PlayableLocalizationView localization,
                                                      SessionQuestionCorrection correction) {
        LanguageCode language = LanguageCode.of(localization.languageCode());
        String prompt = correction.promptFor(language).orElse(localization.prompt());
        List<PlayableOptionTextView> optionTexts = localization.optionTexts().stream()
                .map(text -> correction.optionTextFor(language, text.optionId())
                        .map(corrected -> new PlayableOptionTextView(text.optionId(), corrected))
                        .orElse(text))
                .collect(Collectors.toCollection(ArrayList::new));
        appendNewOptionTexts(correction, language, optionTexts);
        return new PlayableLocalizationView(localization.languageCode(), prompt,
                // An explanation written for the question as it was may
                // no longer match the corrected answer key, and it is
                // reveal-time material either way — the safe thing when a
                // question changes underneath it is to say nothing.
                correction.promptFor(language).isPresent() ? null : localization.explanation(),
                List.copyOf(optionTexts));
    }

    private static void appendNewOptionTexts(SessionQuestionCorrection correction,
                                             LanguageCode language,
                                             List<PlayableOptionTextView> optionTexts) {
        Set<UUID> present = optionTexts.stream()
                .map(PlayableOptionTextView::optionId)
                .collect(Collectors.toSet());
        correction.optionTexts().stream()
                .filter(text -> text.languageCode().equals(language))
                .filter(text -> !present.contains(text.optionId()))
                .map(text -> new PlayableOptionTextView(text.optionId(), text.text()))
                .forEach(optionTexts::add);
    }
}
