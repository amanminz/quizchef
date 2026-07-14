package io.quizchef.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.quizchef.identity.domain.IdentitySession;
import io.quizchef.identity.infrastructure.persistence.IdentitySessionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdentitySessionQueryServiceTest {

    @Mock
    private IdentitySessionRepository identitySessionRepository;

    @InjectMocks
    private IdentitySessionQueryService service;

    private final UUID identityId = UUID.randomUUID();

    @Test
    void shouldConfirmActiveSessionOfMatchingIdentity() {
        IdentitySession session = IdentitySession.start(identityId, "JUnit", "127.0.0.1", null);
        when(identitySessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThat(service.isSessionActive(session.getId(), identityId)).isTrue();
    }

    @Test
    void shouldRejectRevokedSession() {
        IdentitySession session = IdentitySession.start(identityId, "JUnit", "127.0.0.1", null);
        session.revoke();
        when(identitySessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThat(service.isSessionActive(session.getId(), identityId)).isFalse();
    }

    @Test
    void shouldRejectSessionBelongingToAnotherIdentity() {
        IdentitySession session = IdentitySession.start(identityId, "JUnit", "127.0.0.1", null);
        when(identitySessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThat(service.isSessionActive(session.getId(), UUID.randomUUID())).isFalse();
    }

    @Test
    void shouldRejectMissingSession() {
        UUID unknownSessionId = UUID.randomUUID();
        when(identitySessionRepository.findById(unknownSessionId)).thenReturn(Optional.empty());

        assertThat(service.isSessionActive(unknownSessionId, identityId)).isFalse();
    }
}
