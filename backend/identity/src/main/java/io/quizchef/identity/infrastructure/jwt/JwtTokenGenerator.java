package io.quizchef.identity.infrastructure.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Role;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Issues signed access tokens (HMAC-SHA256).
 *
 * <p>Every token is bound to an IdentitySession through the
 * {@code sessionId} claim: cryptographic validation stays stateless, while
 * revocation is enforced against the durable session record — no token
 * blacklist, no token store.
 */
@Component
public class JwtTokenGenerator {

    static final String CLAIM_IDENTITY_TYPE = "identityType";
    static final String CLAIM_ROLES = "roles";
    static final String CLAIM_SESSION_ID = "sessionId";

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;

    public JwtTokenGenerator(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken generate(UUID identityId, UUID sessionId, IdentityType identityType, Set<Role> roles) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        var builder = Jwts.builder()
                .issuer(properties.issuer())
                .subject(identityId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_SESSION_ID, sessionId.toString())
                .claim(CLAIM_IDENTITY_TYPE, identityType.name())
                .claim(CLAIM_ROLES, roles.stream().map(Enum::name).sorted().toList());
        if (properties.hasAudience()) {
            builder.audience().add(properties.audience());
        }
        return new IssuedToken(builder.signWith(signingKey).compact(), expiresAt);
    }
}
