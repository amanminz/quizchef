package io.quizchef.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class GuestTokenDigestTest {

    @Test
    void aDigestNeverContainsTheTokenItWasMadeFrom() {
        GuestParticipantToken token = GuestParticipantToken.generate();

        GuestTokenDigest digest = GuestTokenDigest.of(token);

        assertThat(digest.value()).doesNotContain(token.value());
        assertThat(digest.value()).hasSize(64); // SHA-256, hex
    }

    @Test
    void theSameTokenAlwaysDigestsTheSameWay() {
        // The whole scheme rests on this: the server keeps only the digest,
        // so a returning player is recognized by re-deriving it.
        GuestParticipantToken token = GuestParticipantToken.of("a-known-token");

        assertThat(GuestTokenDigest.of(token)).isEqualTo(GuestTokenDigest.of(token));
    }

    @Test
    void differentTokensDigestDifferently() {
        assertThat(GuestTokenDigest.of(GuestParticipantToken.of("one")))
                .isNotEqualTo(GuestTokenDigest.of(GuestParticipantToken.of("two")));
    }

    @Test
    void matchesOnlyTheTokenItWasMadeFrom() {
        GuestParticipantToken token = GuestParticipantToken.generate();
        GuestTokenDigest digest = GuestTokenDigest.of(token);

        assertThat(digest.matches(token)).isTrue();
        assertThat(digest.matches(GuestParticipantToken.generate())).isFalse();
        assertThat(digest.matches(null)).isFalse();
    }

    @Test
    void aStoredDigestRoundTripsWithoutBeingRehashed() {
        // The persistence layer reads back a digest, not a token. Hashing it
        // again would produce a value that matches nothing.
        GuestParticipantToken token = GuestParticipantToken.generate();
        GuestTokenDigest stored = GuestTokenDigest.ofStoredValue(GuestTokenDigest.of(token).value());

        assertThat(stored.matches(token)).isTrue();
    }

    @Test
    void rejectsABlankDigest() {
        assertThatIllegalArgumentException().isThrownBy(() -> GuestTokenDigest.ofStoredValue(" "));
    }

    @Test
    void theTokenItselfNeverRendersInAString() {
        GuestParticipantToken token = GuestParticipantToken.generate();

        // A credential that reaches a log line through a toString is a
        // leaked credential.
        assertThat(token.toString()).doesNotContain(token.value());
    }
}
