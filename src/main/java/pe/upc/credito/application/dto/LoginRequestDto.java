package pe.upc.credito.application.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDto(
        @NotBlank(message = "Identifier is required") String identifier,
        @NotBlank(message = "Password is required") String password) {
}
