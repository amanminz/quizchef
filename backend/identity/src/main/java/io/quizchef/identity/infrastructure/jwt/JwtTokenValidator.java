package io.quizchef.identity.infrastructure.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Role;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Verifies access tokens: signature, issuer, expiry, and claim shape.
 */
@Component
public class JwtTokenValidator {

    private final JwtParser parser;
    private final String requiredAudience;

    public JwtTokenValidator(JwtProperties properties, Clock clock) {
        this.parser = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8)))
                .requireIssuer(properties.issuer())
                .clock(() -> Date.from(clock.instant()))
                .build();
        this.requiredAudience = properties.audience();
    }

    public IdentityToken validate(String token) {
        Claims claims = parseClaims(token);
        requireAudience(claims);
        try {
            return new IdentityToken(
                    UUID.fromString(claims.getSubject()),
                    IdentityType.valueOf(claims.get(JwtTokenGenerator.CLAIM_IDENTITY_TYPE, String.class)),
                    parseRoles(claims.get(JwtTokenGenerator.CLAIM_ROLES)),
                    claims.getExpiration().toInstant());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw InvalidTokenException.malformed();
        }
    }

    private Claims parseClaims(String token) {
        try {
            return parser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException exception) {
            throw InvalidTokenException.expired();
        } catch (JwtException | IllegalArgumentException exception) {
            throw InvalidTokenException.malformed();
        }
    }

    private void requireAudience(Claims claims) {
        if (requiredAudience == null) {
            return;
        }
        Set<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(requiredAudience)) {
            throw InvalidTokenException.malformed();
        }
    }

    private Set<Role> parseRoles(Object rolesClaim) {
        return switch (rolesClaim) {
            case null -> Set.of();
            case List<?> names -> names.stream()
                    .map(String::valueOf)
                    .map(Role::valueOf)
                    .collect(Collectors.toUnmodifiableSet());
            default -> throw InvalidTokenException.malformed();
        };
    }
}
