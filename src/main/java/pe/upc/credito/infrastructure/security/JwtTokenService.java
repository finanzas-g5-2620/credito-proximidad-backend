package pe.upc.credito.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.upc.credito.application.exception.BusinessException;
import pe.upc.credito.application.support.AuthPrincipal;
import pe.upc.credito.infrastructure.persistence.entity.UserEntity;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Service
public class JwtTokenService {

    private static final Duration TOKEN_TTL = Duration.ofHours(8);
    private static final Set<String> ROLES = Set.of("SUPER_ADMIN", "STORE_ADMIN", "CUSTOMER");

    private final SecretKey signingKey;

    public JwtTokenService(@Value("${app.auth.jwt-secret:}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(UserEntity user, Integer customerId) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .issuer("proximity-credit")
                .subject(user.getUserId().toString())
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(TOKEN_TTL)))
                .claim("userId", user.getUserId())
                .claim("username", user.getUsername())
                .claim("role", user.getUserType());

        if (user.getStoreId() != null) {
            builder.claim("storeId", user.getStoreId());
        }
        if (customerId != null) {
            builder.claim("customerId", customerId);
        }
        return builder.signWith(signingKey, Jwts.SIG.HS256).compact();
    }

    public AuthPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer("proximity-credit")
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Integer userId = integerClaim(claims, "userId");
        String username = claims.get("username", String.class);
        String role = claims.get("role", String.class);
        Integer storeId = nullableIntegerClaim(claims, "storeId");
        Integer customerId = nullableIntegerClaim(claims, "customerId");

        if (userId == null || userId < 1 || !userId.toString().equals(claims.getSubject())
                || username == null || username.isBlank()
                || role == null || !ROLES.contains(role)) {
            throw new IllegalArgumentException("Token claims are incomplete");
        }
        if ("SUPER_ADMIN".equals(role) != (storeId == null)
                || "CUSTOMER".equals(role) != (customerId != null)) {
            throw new IllegalArgumentException("Token scope does not match its role");
        }
        return new AuthPrincipal(userId, username, role, storeId, customerId);
    }

    private Integer integerClaim(Claims claims, String name) {
        Integer value = nullableIntegerClaim(claims, name);
        if (value == null) {
            throw new IllegalArgumentException("Missing token claim: " + name);
        }
        return value;
    }

    private Integer nullableIntegerClaim(Claims claims, String name) {
        Object value = claims.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("Invalid token claim: " + name);
    }
}
