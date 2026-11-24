package pe.upc.credito.infrastructure.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.upc.credito.infrastructure.persistence.entity.UserEntity;
import pe.upc.credito.infrastructure.persistence.repository.UserRepository;

@Component
@Order(2)
@ConditionalOnProperty(name = "app.initial-admin.required", havingValue = "true")
class InitialAdminSeeder implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String email;
    private final String password;

    InitialAdminSeeder(UserRepository users, PasswordEncoder encoder,
                       @Value("${BOOTSTRAP_ADMIN_USERNAME:}") String username,
                       @Value("${BOOTSTRAP_ADMIN_EMAIL:}") String email,
                       @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByUserTypeAndStatus("SUPER_ADMIN", "ACTIVE")) {
            return;
        }
        if (username.isBlank() || email.isBlank() || password.length() < 12
                || users.existsByUsernameIgnoreCase(username) || users.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException("Set unique initial administrator credentials and a password of at least 12 characters");
        }

        UserEntity admin = new UserEntity();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setFirstName("Administrador");
        admin.setLastName("Global");
        admin.setPasswordHash(encoder.encode(password));
        admin.setUserType("SUPER_ADMIN");
        admin.setStatus("ACTIVE");
        users.save(admin);
    }
}
