package pe.upc.credito.application.support;

import pe.upc.credito.infrastructure.persistence.entity.UserEntity;

/** Nombre completo para los DTOs (users.first_name + users.last_name). */
public final class Names {

    private Names() {
    }

    public static String fullName(UserEntity user, Integer fallbackId) {
        if (user == null) {
            return "Cliente " + fallbackId;
        }
        String full = (user.getFirstName() + " " + user.getLastName()).trim();
        return full.isEmpty() ? "Cliente " + fallbackId : full;
    }
}
