package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.quizchef.identity.domain.Credentials;
import io.quizchef.identity.domain.Identity;
import io.quizchef.identity.domain.IdentitySession;
import io.quizchef.identity.domain.IdentityStatus;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.UserProfile;
import io.quizchef.identity.infrastructure.persistence.CredentialsRepository;
import io.quizchef.identity.infrastructure.persistence.IdentityRepository;
import io.quizchef.identity.infrastructure.persistence.IdentitySessionRepository;
import io.quizchef.identity.infrastructure.persistence.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full application against a real PostgreSQL container: Flyway
 * migrations run, Hibernate validates the schema, and the identity aggregates
 * persist and reload through their repositories.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class IdentityPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private IdentityRepository identityRepository;

    @Autowired
    private CredentialsRepository credentialsRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private IdentitySessionRepository identitySessionRepository;

    @Test
    void shouldPersistAndReloadAllIdentityAggregates() {
        Identity identity = identityRepository.save(Identity.registered());
        credentialsRepository.save(Credentials.create(identity.getId(), "$argon2id$stub-hash"));
        userProfileRepository.save(UserProfile.create(identity.getId(), "Aman", "  Aman@Example.COM "));
        identitySessionRepository.save(IdentitySession.start(identity.getId(), "JUnit", "127.0.0.1", null));

        Identity reloaded = identityRepository.findById(identity.getId()).orElseThrow();
        assertThat(reloaded.getIdentityType()).isEqualTo(IdentityType.REGISTERED);
        assertThat(reloaded.getStatus()).isEqualTo(IdentityStatus.ACTIVE);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();

        assertThat(credentialsRepository.findByIdentityId(identity.getId())).isPresent();
        assertThat(userProfileRepository.findByEmail("aman@example.com")).isPresent();
        assertThat(identitySessionRepository.findByIdentityIdAndRevokedFalse(identity.getId())).hasSize(1);
    }

    @Test
    void shouldEnforceUniqueEmailAtDatabaseLevel() {
        Identity first = identityRepository.save(Identity.registered());
        Identity second = identityRepository.save(Identity.registered());
        userProfileRepository.saveAndFlush(UserProfile.create(first.getId(), "First", "duplicate@example.com"));

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> userProfileRepository.saveAndFlush(
                        UserProfile.create(second.getId(), "Second", "Duplicate@example.com")));
    }

    @Test
    void shouldEnforceOneCredentialPerIdentity() {
        Identity identity = identityRepository.save(Identity.registered());
        credentialsRepository.saveAndFlush(Credentials.create(identity.getId(), "hash-one"));

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> credentialsRepository.saveAndFlush(
                        Credentials.create(identity.getId(), "hash-two")));
    }
}
