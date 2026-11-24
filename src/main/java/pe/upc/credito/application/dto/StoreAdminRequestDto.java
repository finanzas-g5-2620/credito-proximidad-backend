package pe.upc.credito.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StoreAdminRequestDto(
        @NotBlank(message = "First name is required") @Size(max = 100) String first_name,
        @NotBlank(message = "Last name is required") @Size(max = 100) String last_name,
        @NotBlank(message = "Email is required") @Email @Size(max = 100) String email,
        @NotBlank(message = "Username is required")
        @Pattern(regexp = "^\\s*[A-Za-z0-9._-]{3,50}\\s*$", message = "Username must be 3-50 letters, digits, dots, underscores or hyphens")
        @Size(max = 50) String username,
        @NotBlank(message = "Password is required") @Size(min = 6, max = 72) String password) {
}
