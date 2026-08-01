package io.quizchef.session.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable gameplay-engine constants that are not part of a quiz's own
 * authored settings — global today, the natural seam to make per-quiz if a
 * future milestone demands it. Same idiom as {@code CorsProperties}/{@code
 * RateLimitProperties}/{@code JwtProperties}.
 *
 * @param questionPreviewSeconds how long a question's reading period lasts
 *                               before options appear and the answer timer
 *                               starts (see {@code QUESTION_PREVIEW})
 */
@ConfigurationProperties(prefix = "quizchef.gameplay")
public record GameplayProperties(int questionPreviewSeconds) {

    public GameplayProperties {
        if (questionPreviewSeconds <= 0) {
            throw new IllegalArgumentException(
                    "quizchef.gameplay.question-preview-seconds must be positive");
        }
    }
}
