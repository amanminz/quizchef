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
 */
@Component
public class JwtTokenGenerator {

    static final String CLAIM_IDENTITY_TYPE = "identityType";
    static final String CLAIM_ROLES = "roles";

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;

    public JwtTokenGenerator(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generate(UUID identityId, IdentityType identityType, Set<Role> roles) {
        Instant now = clock.instant();
        var builder = Jwts.builder()
                .issuer(properties.issuer())
                .subject(identityId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .claim(CLAIM_IDENTITY_TYPE, identityType.name())
                .claim(CLAIM_ROLES, roles.stream().map(Enum::name).sorted().toList());
        if (properties.hasAudience()) {
            builder.audience().add(properties.audience());
        }
        return builder.signWith(signingKey).compact();
    }
}
