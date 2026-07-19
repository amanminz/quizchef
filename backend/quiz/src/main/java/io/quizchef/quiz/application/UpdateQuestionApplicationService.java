package io.quizchef.quiz.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.Option;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.QuestionLocalization;
import io.quizchef.quiz.domain.QuestionType;
import io.quizchef.quiz.domain.Tag;
import io.quizchef.quiz.domain.exception.QuestionModifiedConcurrentlyException;
import io.quizchef.quiz.domain.exception.QuestionNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces a draft question's full editable representation: question type,
 * default language, difficulty, options, every localization, references,
 * and tags. The aggregate decides
 * what the lifecycle allows — published and archived questions reject all
 * of it.
 *
 * <p>Lost updates are rejected: the caller sends the version it last read,
 * and a mismatch means someone else saved in between (409).
 */
@Service
public class UpdateQuestionApplicationService {

    private final QuestionRepository questionRepository;
    private final AuthorizationService authorizationService;
    private final TagResolver tagResolver;

    public UpdateQuestionApplicationService(QuestionRepository questionRepository,
                                            AuthorizationService authorizationService,
                                            TagResolver tagResolver) {
        this.questionRepository = questionRepository;
        this.authorizationService = authorizationService;
        this.tagResolver = tagResolver;
    }

    @Transactional
    public QuestionView update(CurrentUser currentUser, UpdateQuestionCommand command) {
        authorizationService.authorize(currentUser, Permission.QUIZ_EDIT);
        Question question = questionRepository.findById(command.questionId())
                .orElseThrow(() -> new QuestionNotFoundException(command.questionId()));
        QuestionOwnership.requireOwner(currentUser, question);
        if (command.version() != question.getVersion()) {
            throw new QuestionModifiedConcurrentlyException();
        }

        question.changeDifficulty(command.difficulty());
        replaceOptionsAndLocalizations(question, command);
        question.updateBibleReferences(command.bibleReferences());
        question.updateMediaReferences(command.mediaReferences());
        List<Tag> tags = tagResolver.resolve(command.tags());
        question.updateTags(tagIdsOf(tags));

        questionRepository.saveAndFlush(question);
        return QuestionView.of(question, tags);
    }

    /**
     * The localizations are the complete new set: each entry is upserted,
     * every language not in the list is removed. The structure (type,
     * default language, options) is replaced first in one validated step,
     * using the target default language's entry — which is why that entry
     * must always be present. Type and default language only differ for
     * drafts; the aggregate rejects everything else anyway.
     */
    private static void replaceOptionsAndLocalizations(Question question, UpdateQuestionCommand command) {
        QuestionType questionType = command.questionType() != null
                ? command.questionType()
                : question.getQuestionType();
        LanguageCode defaultLanguage = command.defaultLanguage() != null
                ? LanguageCode.of(command.defaultLanguage())
                : question.getDefaultLanguage();
        QuestionContentCommand defaultContent = command.localizations().stream()
                .filter(content -> LanguageCode.of(content.languageCode()).equals(defaultLanguage))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "localizations must include the default language (%s)"
                                .formatted(defaultLanguage.value())));

        List<Option> options = command.options().stream()
                .map(option -> new Option(option.id(), option.correct(), option.displayOrder()))
                .toList();
        question.replaceStructure(questionType, defaultContent.toLocalization(),
                options, defaultContent.toOptionTexts());

        Set<LanguageCode> provided = new HashSet<>();
        for (QuestionContentCommand content : command.localizations()) {
            LanguageCode language = LanguageCode.of(content.languageCode());
            if (!provided.add(language)) {
                throw new IllegalArgumentException(
                        "language %s appears twice".formatted(language.value()));
            }
            question.localize(content.toLocalization(), content.toOptionTexts());
        }
        question.localizations().stream()
                .map(QuestionLocalization::languageCode)
                .filter(language -> !provided.contains(language))
                .forEach(question::removeLocalization);
    }

    private static Set<UUID> tagIdsOf(List<Tag> tags) {
        Set<UUID> ids = new LinkedHashSet<>();
        tags.forEach(tag -> ids.add(tag.getId()));
        return ids;
    }
}
