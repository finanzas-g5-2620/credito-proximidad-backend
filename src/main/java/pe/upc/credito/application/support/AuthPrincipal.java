package pe.upc.credito.application.support;

/** Claims established by a verified access token. */
public record AuthPrincipal(
        Integer userId,
        String username,
        String role,
        Integer storeId,
        Integer customerId) {
}
