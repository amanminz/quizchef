package io.quizchef.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.quizchef.identity.domain.Identity;
import io.quizchef.identity.domain.IdentitySession;
import io.quizchef.identity.domain.Role;
import io.quizchef.identity.infrastructure.persistence.IdentityRepository;
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

    @Mock
    private IdentityRepository identityRepository;

    @InjectMocks
    private IdentitySessionQueryService service;

    private final Identity identity = Identity.registered();

    @Test
    void answersTheIdentitysPersistedRolesForAnActiveSession() {
        IdentitySession session = IdentitySession.start(identity.getId(), "JUnit", "127.0.0.1", null);
        when(identitySessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(identityRepository.findById(identity.getId())).thenReturn(Optional.of(identity));

        assertThat(service.activeSessionRoles(session.getId(), identity.getId()))
                .contains(java.util.Set.of(Role.USER));
    }

    @Test
    void reflectsARoleGrantedAfterTheTokenWasIssued() {
        identity.grantRole(Role.QUIZ_MASTER);
        IdentitySession session = IdentitySession.start(identity.getId(), "JUnit", "127.0.0.1", null);
        when(identitySessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(identityRepository.findById(identity.getId())).thenReturn(Optional.of(identity));

        assertThat(service.activeSessionRoles(session.getId(), identity.getId()).orElseThrow())
                .containsExactlyInAnyOrder(Role.USER, Role.QUIZ_MASTER);
    }

    @Test
    void rejectsARevokedSession() {
        IdentitySession session = IdentitySession.start(identity.getId(), "JUnit", "127.0.0.1", null);
        session.revoke();
        when(identitySessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThat(service.activeSessionRoles(session.getId(), identity.getId())).isEmpty();
    }

    @Test
    void rejectsASessionBelongingToAnotherIdentity() {
        IdentitySession session = IdentitySession.start(identity.getId(), "JUnit", "127.0.0.1", null);
        when(identitySessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThat(service.activeSessionRoles(session.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void rejectsAMissingSession() {
        UUID unknownSessionId = UUID.randomUUID();
        when(identitySessionRepository.findById(unknownSessionId)).thenReturn(Optional.empty());

        assertThat(service.activeSessionRoles(unknownSessionId, identity.getId())).isEmpty();
    }

    @Test
    void rejectsADisabledIdentityEvenWithALiveSession() {
        identity.disable();
        IdentitySession session = IdentitySession.start(identity.getId(), "JUnit", "127.0.0.1", null);
        when(identitySessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(identityRepository.findById(identity.getId())).thenReturn(Optional.of(identity));

        assertThat(service.activeSessionRoles(session.getId(), identity.getId())).isEmpty();
    }
}
