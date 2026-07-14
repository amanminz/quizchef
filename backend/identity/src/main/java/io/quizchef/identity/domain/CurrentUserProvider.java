package io.quizchef.identity.domain;

/**
 * Domain port that supplies the {@link CurrentUser} of the running request.
 *
 * <p>Implemented by framework adapters (for example on top of Spring
 * Security's context). Business services inject this interface and stay
 * framework-free.
 */
public interface CurrentUserProvider {

    CurrentUser currentUser();
}
