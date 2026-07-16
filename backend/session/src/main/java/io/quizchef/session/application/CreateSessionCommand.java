package io.quizchef.session.application;

import java.util.UUID;

/**
 * Creates a live session for a published quiz version. The host is always
 * the authenticated caller — never part of the command.
 */
public record CreateSessionCommand(UUID publishedQuizVersionId) {
}
