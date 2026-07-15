package io.quizchef.quiz.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.Option;
import io.quizchef.quiz.domain.OptionLocalization;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.QuestionLocalization;
import io.quizchef.quiz.domain.Tag;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a draft question for the authenticated caller. The owner is
 * always {@code CurrentUser.reference()} — never accepted from the client
 * — and the authoring API only creates MANUAL questions.
 */
@Service
public class CreateQuestionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(CreateQuestionApplicationService.class);

    private final QuestionRepository questionRepository;
    private final AuthorizationService authorizationService;
    private final TagResolver tagResolver;

    public CreateQuestionApplicationService(QuestionRepository questionRepository,
                                            AuthorizationService authorizationService,
                                            TagResolver tagResolver) {
        this.questionRepository = questionRepository;
        this.authorizationService = authorizationService;
        this.tagResolver = tagResolver;
    }

    @Transactional
    public QuestionView create(CurrentUser currentUser, CreateQuestionCommand command) {
        authorizationService.authorize(currentUser, Permission.QUIZ_CREATE);

        LanguageCode defaultLanguage = LanguageCode.of(command.defaultLanguage());
        QuestionLocalization defaultContent = new QuestionLocalization(
                defaultLanguage, command.title(), command.prompt(), command.explanation());

        List<Option> options = new ArrayList<>();
        List<OptionLocalization> defaultTexts = new ArrayList<>();
        for (CreateQuestionCommand.CreateQuestionOptionCommand optionCommand : command.options()) {
            Option option = Option.of(optionCommand.correct(), optionCommand.displayOrder());
            options.add(option);
            defaultTexts.add(option.localized(defaultLanguage, optionCommand.text()));
        }

        Question question = Question.create(defaultContent, currentUser.reference(),
                command.questionType(), command.difficulty(), options, defaultTexts);
        if (command.bibleReferences() != null) {
            question.updateBibleReferences(command.bibleReferences());
        }
        if (command.mediaReferences() != null) {
            question.updateMediaReferences(command.mediaReferences());
        }
        List<Tag> tags = tagResolver.resolve(command.tags() == null ? List.of() : command.tags());
        question.updateTags(tagIdsOf(tags));
        questionRepository.save(question);

        log.info("Question {} created by identity {}", question.getId(), currentUser.identityId());
        return QuestionView.of(question, tags);
    }

    private static Set<UUID> tagIdsOf(List<Tag> tags) {
        Set<UUID> ids = new LinkedHashSet<>();
        tags.forEach(tag -> ids.add(tag.getId()));
        return ids;
    }
}
