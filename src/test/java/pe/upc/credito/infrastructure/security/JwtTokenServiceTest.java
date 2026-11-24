package pe.upc.credito.infrastructure.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import pe.upc.credito.application.support.AuthPrincipal;
import pe.upc.credito.infrastructure.persistence.entity.UserEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTest {

    private static final String SECRET = "local-test-secret-with-at-least-thirty-two-bytes";

    @Test
    void issuedTokenCarriesSignedIdentityAndScopes() {
        JwtTokenService tokens = new JwtTokenService(SECRET);
        UserEntity user = user(17, 4, "CUSTOMER");

        AuthPrincipal principal = tokens.parse(tokens.issue(user, 26));

        assertEquals(17, principal.userId());
        assertEquals("maria", principal.username());
        assertEquals("CUSTOMER", principal.role());
        assertEquals(4, principal.storeId());
        assertEquals(26, principal.customerId());
    }

    @Test
    void systemAdministratorHasNoStoreOrCustomerScope() {
        JwtTokenService tokens = new JwtTokenService(SECRET);
        UserEntity user = user(1, null, "SUPER_ADMIN");

        AuthPrincipal principal = tokens.parse(tokens.issue(user, null));

        assertNull(principal.storeId());
        assertNull(principal.customerId());
    }

    @Test
    void rejectsExpiredOrMalformedToken() {
        JwtTokenService tokens = new JwtTokenService(SECRET);

        assertThrows(JwtException.class, () -> tokens.parse("not.a.jwt"));
    }

    @Test
    void requiresASecretLongEnoughForHs256() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenService("too-short"));
    }

    private UserEntity user(Integer id, Integer storeId, String role) {
        UserEntity user = new UserEntity();
        user.setUserId(id);
        user.setStoreId(storeId);
        user.setUsername("maria");
        user.setUserType(role);
        return user;
    }
}
