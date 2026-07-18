package io.quizchef.identity.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Identity;
import io.quizchef.identity.domain.Permission;
import io.quizchef.identity.domain.Role;
import io.quizchef.identity.domain.event.HostAccessGrantedEvent;
import io.quizchef.identity.domain.exception.IdentityNotFoundException;
import io.quizchef.identity.infrastructure.persistence.IdentityRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service host onboarding: grants the caller the QUIZ_MASTER role,
 * durably and idempotently. The product rule (documented in RFC-002) is
 * automatic promotion — a self-hosted, church-scale platform trusts its
 * own registered users to author and host; an approval gate can be
 * inserted here later without changing the API shape (the response
 * status already reserves PENDING/DENIED).
 *
 * <p>Gated by USER_PROFILE_UPDATE — modifying one's own account — which
 * every registered USER holds and no guest does, so guests are refused by
 * the ordinary permission path rather than a special case. Because
 * request-time authorization reads persisted roles (see
 * IdentitySessionQueryService), the grant takes effect on the caller's
 * very next request with the same token.
 */
@Service
public class RequestHostAccessApplicationService {

    private static final Logger log = LoggerFactory.getLogger(RequestHostAccessApplicationService.class);

    private final IdentityRepository identityRepository;
    private final AuthorizationService authorizationService;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public RequestHostAccessApplicationService(IdentityRepository identityRepository,
                                               AuthorizationService authorizationService,
                                               DomainEventPublisher eventPublisher,
                                               Clock clock) {
        this.identityRepository = identityRepository;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public HostAccessView request(CurrentUser currentUser) {
        authorizationService.authorize(currentUser, Permission.USER_PROFILE_UPDATE);

        Identity identity = identityRepository.findById(currentUser.identityId())
                .orElseThrow(() -> new IdentityNotFoundException(currentUser.identityId()));

        boolean granted = identity.grantRole(Role.QUIZ_MASTER);
        if (granted) {
            identityRepository.save(identity);
            eventPublisher.publish(new HostAccessGrantedEvent(identity.reference(), clock.instant()));
            log.info("Identity {} granted host access", identity.getId());
        }

        CurrentUser promoted = CurrentUser.authenticated(
                identity.getId(), identity.getIdentityType(), identity.roles());
        return new HostAccessView(
                HostAccessView.HostAccessStatus.GRANTED,
                identity.roles(),
                authorizationService.permissionsOf(promoted));
    }
}
