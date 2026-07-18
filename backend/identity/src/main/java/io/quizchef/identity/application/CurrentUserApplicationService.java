package io.quizchef.identity.application;

import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.CurrentUserProvider;
import io.quizchef.identity.domain.Permission;
import io.quizchef.identity.domain.UserProfile;
import io.quizchef.identity.infrastructure.persistence.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers "who am I?" for the authenticated caller — the first consumer
 * of the authorization architecture: it depends only on CurrentUser and
 * AuthorizationService, exactly as the architecture prescribes. Since
 * Phase 3 it also carries the caller's own profile basics (display name,
 * email) so the client's profile page is one read, not two.
 */
@Service
public class CurrentUserApplicationService {

    private final CurrentUserProvider currentUserProvider;
    private final AuthorizationService authorizationService;
    private final UserProfileRepository userProfileRepository;

    public CurrentUserApplicationService(CurrentUserProvider currentUserProvider,
                                         AuthorizationService authorizationService,
                                         UserProfileRepository userProfileRepository) {
        this.currentUserProvider = currentUserProvider;
        this.authorizationService = authorizationService;
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional(readOnly = true)
    public CurrentUserView currentUser() {
        CurrentUser currentUser = currentUserProvider.currentUser();
        authorizationService.authorize(currentUser, Permission.USER_PROFILE_READ);
        UserProfile profile = userProfileRepository
                .findByIdentityId(currentUser.identityId()).orElse(null);
        return new CurrentUserView(
                currentUser.identityId(),
                currentUser.identityType(),
                currentUser.roles(),
                authorizationService.permissionsOf(currentUser),
                profile == null ? null : profile.getDisplayName(),
                profile == null ? null : profile.getEmail());
    }
}
