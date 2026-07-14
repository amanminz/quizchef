package io.quizchef.identity.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenTest {

    private static final Instant NOW = Instant.parse("2026-07-14T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final JwtProperties PROPERTIES = new JwtProperties(
            "quizchef-test", "0123456789abcdef0123456789abcdef", Duration.ofMinutes(15), null);

    private final JwtTokenGenerator generator = new JwtTokenGenerator(PROPERTIES, FIXED_CLOCK);
    private final JwtTokenValidator validator = new JwtTokenValidator(PROPERTIES, FIXED_CLOCK);

    @Test
    void shouldRoundTripIdentityTypeAndRoles() {
        UUID identityId = UUID.randomUUID();
        String token = generator.generate(
                identityId, IdentityType.REGISTERED, Set.of(Role.USER, Role.QUIZ_MASTER));

        IdentityToken decoded = validator.validate(token);

        assertThat(decoded.identityId()).isEqualTo(identityId);
        assertThat(decoded.identityType()).isEqualTo(IdentityType.REGISTERED);
        assertThat(decoded.roles()).containsExactlyInAnyOrder(Role.USER, Role.QUIZ_MASTER);
        assertThat(decoded.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void shouldRoundTripGuestWithoutRoles() {
        String token = generator.generate(UUID.randomUUID(), IdentityType.GUEST, Set.of());

        IdentityToken decoded = validator.validate(token);

        assertThat(decoded.identityType()).isEqualTo(IdentityType.GUEST);
        assertThat(decoded.roles()).isEmpty();
    }

    @Test
    void shouldRejectExpiredToken() {
        String token = generator.generate(UUID.randomUUID(), IdentityType.REGISTERED, Set.of(Role.USER));
        Clock afterExpiry = Clock.fixed(NOW.plus(Duration.ofMinutes(16)), ZoneOffset.UTC);
        JwtTokenValidator lateValidator = new JwtTokenValidator(PROPERTIES, afterExpiry);

        assertThatExceptionOfType(InvalidTokenException.class)
                .isThrownBy(() -> lateValidator.validate(token))
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("identity.token.expired"));
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = generator.generate(UUID.randomUUID(), IdentityType.REGISTERED, Set.of(Role.USER));
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatExceptionOfType(InvalidTokenException.class)
                .isThrownBy(() -> validator.validate(tampered))
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo("identity.token.invalid"));
    }

    @Test
    void shouldAssertConfiguredAudience() {
        JwtProperties withAudience = new JwtProperties(
                "quizchef-test", PROPERTIES.secret(), Duration.ofMinutes(15), "quizchef-web");
        JwtTokenGenerator audienceGenerator = new JwtTokenGenerator(withAudience, FIXED_CLOCK);
        JwtTokenValidator audienceValidator = new JwtTokenValidator(withAudience, FIXED_CLOCK);
        UUID identityId = UUID.randomUUID();

        String token = audienceGenerator.generate(identityId, IdentityType.REGISTERED, Set.of(Role.USER));

        assertThat(audienceValidator.validate(token).identityId()).isEqualTo(identityId);
    }

    @Test
    void shouldRejectTokenWithoutConfiguredAudience() {
        JwtProperties withAudience = new JwtProperties(
                "quizchef-test", PROPERTIES.secret(), Duration.ofMinutes(15), "quizchef-web");
        JwtTokenValidator audienceValidator = new JwtTokenValidator(withAudience, FIXED_CLOCK);

        String tokenWithoutAudience = generator.generate(
                UUID.randomUUID(), IdentityType.REGISTERED, Set.of(Role.USER));

        assertThatExceptionOfType(InvalidTokenException.class)
                .isThrownBy(() -> audienceValidator.validate(tokenWithoutAudience));
    }

    @Test
    void shouldTreatBlankAudienceAsAbsent() {
        JwtProperties blankAudience = new JwtProperties(
                "quizchef-test", PROPERTIES.secret(), Duration.ofMinutes(15), "  ");

        assertThat(blankAudience.hasAudience()).isFalse();
    }

    @Test
    void shouldRejectTokenSignedWithDifferentKey() {
        JwtProperties otherKey = new JwtProperties(
                "quizchef-test", "ffffffffffffffffffffffffffffffff", Duration.ofMinutes(15), null);
        String token = new JwtTokenGenerator(otherKey, FIXED_CLOCK)
                .generate(UUID.randomUUID(), IdentityType.REGISTERED, Set.of());

        assertThatExceptionOfType(InvalidTokenException.class)
                .isThrownBy(() -> validator.validate(token));
    }

    @Test
    void shouldRejectTokenFromDifferentIssuer() {
        JwtProperties otherIssuer = new JwtProperties(
                "someone-else", PROPERTIES.secret(), Duration.ofMinutes(15), null);
        String token = new JwtTokenGenerator(otherIssuer, FIXED_CLOCK)
                .generate(UUID.randomUUID(), IdentityType.REGISTERED, Set.of());

        assertThatExceptionOfType(InvalidTokenException.class)
                .isThrownBy(() -> validator.validate(token));
    }

    @Test
    void shouldRejectSecretShorterThan256Bits() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JwtProperties("quizchef-test", "too-short", Duration.ofMinutes(15), null));
    }

    @Test
    void shouldRejectNonPositiveTokenLifetime() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JwtProperties(
                        "quizchef-test", PROPERTIES.secret(), Duration.ZERO, null));
    }
}
