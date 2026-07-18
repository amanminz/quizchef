package io.quizchef.identity.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.domain.Credentials;
import io.quizchef.identity.domain.Identity;
import io.quizchef.identity.domain.IdentitySession;
import io.quizchef.identity.domain.PasswordHasher;
import io.quizchef.identity.domain.Role;
import io.quizchef.identity.domain.UserProfile;
import io.quizchef.identity.domain.event.IdentityAuthenticatedEvent;
import io.quizchef.identity.domain.exception.InvalidCredentialsException;
import io.quizchef.identity.infrastructure.jwt.IssuedToken;
import io.quizchef.identity.infrastructure.jwt.JwtTokenGenerator;
import io.quizchef.identity.infrastructure.persistence.CredentialsRepository;
import io.quizchef.identity.infrastructure.persistence.IdentityRepository;
import io.quizchef.identity.infrastructure.persistence.IdentitySessionRepository;
import io.quizchef.identity.infrastructure.persistence.UserProfileRepository;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authenticates a registered identity: verifies credentials, enforces the
 * single-active-session rule, issues a session-bound JWT, and publishes
 * {@link IdentityAuthenticatedEvent} — all in one transaction.
 *
 * <p>Every failure — unknown email, wrong password, disabled identity —
 * surfaces as the same {@link InvalidCredentialsException} so responses
 * never reveal which condition failed.
 */
@Service
public class AuthenticateIdentityApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticateIdentityApplicationService.class);

    private final UserProfileRepository userProfileRepository;
    private final CredentialsRepository credentialsRepository;
    private final IdentityRepository identityRepository;
    private final IdentitySessionRepository identitySessionRepository;
    private final PasswordHasher passwordHasher;
    private final JwtTokenGenerator tokenGenerator;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Verified against when the email is unknown, so both failure paths pay
     * the same Argon2 cost and response timing does not reveal whether an
     * email is registered.
     */
    private final String timingMaskHash;

    public AuthenticateIdentityApplicationService(UserProfileRepository userProfileRepository,
                                                  CredentialsRepository credentialsRepository,
                                                  IdentityRepository identityRepository,
                                                  IdentitySessionRepository identitySessionRepository,
                                                  PasswordHasher passwordHasher,
                                                  JwtTokenGenerator tokenGenerator,
                                                  DomainEventPublisher eventPublisher,
                                                  Clock clock) {
        this.userProfileRepository = userProfileRepository;
        this.credentialsRepository = credentialsRepository;
        this.identityRepository = identityRepository;
        this.identitySessionRepository = identitySessionRepository;
        this.passwordHasher = passwordHasher;
        this.tokenGenerator = tokenGenerator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.timingMaskHash = passwordHasher.hash("quizchef-timing-mask");
    }

    @Transactional
    public AuthenticationResult authenticate(AuthenticateIdentityCommand command) {
        String email = UserProfile.normalizeEmail(command.email());

        UserProfile profile = userProfileRepository.findByEmail(email)
                .orElseThrow(() -> rejectUnknownEmail(command.password()));

        Credentials credentials = credentialsRepository.findByIdentityId(profile.getIdentityId())
                .orElseThrow(this::reject);
        if (!passwordHasher.matches(command.password(), credentials.getPasswordHash())) {
            log.info("Authentication failed for identity {}", profile.getIdentityId());
            throw new InvalidCredentialsException();
        }

        Identity identity = identityRepository.findById(profile.getIdentityId())
                .orElseThrow(this::reject);
        if (!identity.isActive()) {
            log.info("Authentication rejected for inactive identity {}", identity.getId());
            throw new InvalidCredentialsException();
        }

        revokeActiveSessions(identity);
        IdentitySession session = identitySessionRepository.save(IdentitySession.start(
                identity.getId(), command.userAgent(), command.ipAddress(), null));

        // The token's roles claim reflects the durable roles at issuance;
        // request-time authorization re-reads the persisted roles through
        // the session check, so later grants apply without a new login.
        Set<Role> roles = identity.roles();
        IssuedToken issued = tokenGenerator.generate(
                identity.getId(), session.getId(), identity.getIdentityType(), roles);

        eventPublisher.publish(new IdentityAuthenticatedEvent(identity.reference(), clock.instant()));
        log.info("Identity {} authenticated, session {}", identity.getId(), session.getId());

        return new AuthenticationResult(
                identity.getId(), profile.getDisplayName(), issued.token(), issued.expiresAt(),
                null, roles);
    }

    private void revokeActiveSessions(Identity identity) {
        List<IdentitySession> activeSessions =
                identitySessionRepository.findByIdentityIdAndRevokedFalse(identity.getId());
        activeSessions.forEach(IdentitySession::revoke);
        identitySessionRepository.saveAll(activeSessions);
    }

    /**
     * Unknown email still pays the Argon2 verification cost so the two
     * failure paths are indistinguishable by timing.
     */
    private InvalidCredentialsException rejectUnknownEmail(String password) {
        passwordHasher.matches(password, timingMaskHash);
        log.info("Authentication failed for unknown email");
        return new InvalidCredentialsException();
    }

    private InvalidCredentialsException reject() {
        return new InvalidCredentialsException();
    }
}
