package io.quizchef.quiz.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.QuizLocalization;
import io.quizchef.quiz.domain.exception.QuizModifiedConcurrentlyException;
import io.quizchef.quiz.domain.exception.QuizNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates a quiz's visibility, settings, and localizations. The aggregate
 * decides what the current lifecycle state allows — drafts change freely,
 * published quizzes accept nothing but visibility.
 *
 * <p>Lost updates are rejected: the caller sends the version it last read,
 * and a mismatch means someone else saved in between (409).
 */
@Service
public class UpdateQuizApplicationService {

    private final QuizRepository quizRepository;
    private final AuthorizationService authorizationService;

    public UpdateQuizApplicationService(QuizRepository quizRepository,
                                        AuthorizationService authorizationService) {
        this.quizRepository = quizRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public QuizView update(CurrentUser currentUser, UpdateQuizCommand command) {
        authorizationService.authorize(currentUser, Permission.QUIZ_EDIT);
        Quiz quiz = quizRepository.findById(command.quizId())
                .orElseThrow(() -> new QuizNotFoundException(command.quizId()));
        QuizOwnership.requireOwner(currentUser, quiz);
        if (command.version() != quiz.getVersion()) {
            throw new QuizModifiedConcurrentlyException();
        }

        if (command.settings() != null) {
            quiz.updateSettings(command.settings().toSettings());
        }
        if (command.localizations() != null) {
            replaceLocalizations(quiz, command.localizations());
        }
        if (command.visibility() != null) {
            quiz.changeVisibility(command.visibility());
        }

        quizRepository.saveAndFlush(quiz);
        return QuizView.of(quiz);
    }

    /**
     * The provided list is the complete new set of translations: each entry
     * is upserted, every language not in the list is removed. Dropping the
     * default language is impossible — the aggregate refuses.
     */
    private static void replaceLocalizations(Quiz quiz, List<QuizLocalizationCommand> commands) {
        List<QuizLocalization> localizations = commands.stream()
                .map(QuizLocalizationCommand::toLocalization)
                .toList();
        Set<LanguageCode> provided = new HashSet<>();
        for (QuizLocalization localization : localizations) {
            if (!provided.add(localization.languageCode())) {
                throw new IllegalArgumentException(
                        "language %s appears twice".formatted(localization.languageCode().value()));
            }
        }
        localizations.forEach(quiz::localize);
        quiz.localizations().stream()
                .map(QuizLocalization::languageCode)
                .filter(language -> !provided.contains(language))
                .forEach(quiz::removeLocalization);
    }
}
