package pe.upc.credito.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.upc.credito.application.dto.AuthMeResponseDto;
import pe.upc.credito.application.dto.AuthResponseDto;
import pe.upc.credito.application.dto.LoginRequestDto;
import pe.upc.credito.application.service.AuthService;
import pe.upc.credito.application.support.TenantContext;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Sign in and inspect the current authenticated identity")
public class AuthController {

    private final AuthService authService;
    private final TenantContext tenant;

    public AuthController(AuthService authService, TenantContext tenant) {
        this.authService = authService;
        this.tenant = tenant;
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in", description = "Verifies the credentials and returns a JWT valid for eight hours.")
    public AuthResponseDto login(@Valid @RequestBody LoginRequestDto request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Read the current session",
            description = "Returns the current account, email, role and store context.")
    public AuthMeResponseDto me() {
        return authService.currentIdentity(tenant.currentIdentity().userId());
    }
}
