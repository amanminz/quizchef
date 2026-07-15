package io.quizchef.identity.application;

import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.CurrentUserProvider;
import io.quizchef.identity.domain.Permission;
import org.springframework.stereotype.Service;

/**
 * Answers "who am I?" for the authenticated caller — the first consumer
 * of the authorization architecture: it depends only on CurrentUser and
 * AuthorizationService, exactly as the architecture prescribes.
 */
@Service
public class CurrentUserApplicationService {

    private final CurrentUserProvider currentUserProvider;
    private final AuthorizationService authorizationService;

    public CurrentUserApplicationService(CurrentUserProvider currentUserProvider,
                                         AuthorizationService authorizationService) {
        this.currentUserProvider = currentUserProvider;
        this.authorizationService = authorizationService;
    }

    public CurrentUserView currentUser() {
        CurrentUser currentUser = currentUserProvider.currentUser();
        authorizationService.authorize(currentUser, Permission.USER_PROFILE_READ);
        return new CurrentUserView(
                currentUser.identityId(),
                currentUser.identityType(),
                currentUser.roles(),
                authorizationService.permissionsOf(currentUser));
    }
}
