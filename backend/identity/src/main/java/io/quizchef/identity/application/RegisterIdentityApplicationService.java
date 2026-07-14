package io.quizchef.identity.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.domain.Credentials;
import io.quizchef.identity.domain.Identity;
import io.quizchef.identity.domain.PasswordHasher;
import io.quizchef.identity.domain.UserProfile;
import io.quizchef.identity.domain.event.IdentityRegisteredEvent;
import io.quizchef.identity.domain.exception.DuplicateEmailException;
import io.quizchef.identity.infrastructure.persistence.CredentialsRepository;
import io.quizchef.identity.infrastructure.persistence.IdentityRepository;
import io.quizchef.identity.infrastructure.persistence.UserProfileRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers a new identity: one transaction creating Identity, Credentials,
 * and UserProfile, then publishing {@link IdentityRegisteredEvent}.
 *
 * <p>No IdentitySession is created — that belongs to login.
 */
@Service
public class RegisterIdentityApplicationService {

    private static final Logger log = LoggerFactory.getLogger(RegisterIdentityApplicationService.class);

    private final IdentityRepository identityRepository;
    private final CredentialsRepository credentialsRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordHasher passwordHasher;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public RegisterIdentityApplicationService(IdentityRepository identityRepository,
                                              CredentialsRepository credentialsRepository,
                                              UserProfileRepository userProfileRepository,
                                              PasswordHasher passwordHasher,
                                              DomainEventPublisher eventPublisher,
                                              Clock clock) {
        this.identityRepository = identityRepository;
        this.credentialsRepository = credentialsRepository;
        this.userProfileRepository = userProfileRepository;
        this.passwordHasher = passwordHasher;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public RegisteredIdentity register(RegisterIdentityCommand command) {
        String email = UserProfile.normalizeEmail(command.email());
        if (userProfileRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        Identity identity = Identity.registered();
        Credentials credentials = Credentials.create(identity.getId(), passwordHasher.hash(command.password()));
        UserProfile profile = UserProfile.create(identity.getId(), command.displayName(), email);
        if (command.phoneNumber() != null && !command.phoneNumber().isBlank()) {
            profile.changePhoneNumber(command.phoneNumber().strip());
        }

        identityRepository.save(identity);
        credentialsRepository.save(credentials);
        saveProfileGuardingDuplicates(profile);

        eventPublisher.publish(new IdentityRegisteredEvent(identity.reference(), clock.instant()));
        log.info("Identity {} registered", identity.getId());

        return new RegisteredIdentity(
                identity.getId(), profile.getDisplayName(), profile.getEmail(), identity.getCreatedAt());
    }

    /**
     * The unique index is the authority: two concurrent registrations with the
     * same email both pass the precondition check, but only one survives the
     * flush. The loser gets the same 409 as if the check had caught it.
     */
    private void saveProfileGuardingDuplicates(UserProfile profile) {
        try {
            userProfileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateEmailException();
        }
    }
}
