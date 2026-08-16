package io.quizchef.session.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The host's correction of one question inside one running session: what it
 * should say, and which of its options are actually correct.
 *
 * <p>States the question as it should now be, rather than a patch on what
 * it was — the host is looking at the question and submitting the fixed
 * version. Only the languages present are changed; the rest keep their
 * authored text.
 */
public record CorrectQuestionCommand(
        UUID sessionId,
        UUID questionId,
        Set<UUID> correctOptionIds,
        List<CorrectedLocalization> localizations
) {

    /** The corrected wording in one language. */
    public record CorrectedLocalization(
            String languageCode,
            String prompt,
            List<CorrectedOption> options
    ) {
    }

    /** One option's corrected wording. The option itself is the authored one. */
    public record CorrectedOption(UUID optionId, String text) {
    }
}
