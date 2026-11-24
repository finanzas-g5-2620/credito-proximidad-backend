package pe.upc.credito.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StoreCreateRequestDto(
        @NotBlank(message = "Business name is required") @Size(max = 100) String business_name,
        @NotBlank(message = "Tax ID is required") @Pattern(regexp = "^[0-9]{11}$", message = "Tax ID must contain 11 digits") String tax_id,
        @NotBlank(message = "Address is required") @Size(max = 150) String address,
        @Size(max = 20) String phone_number,
        @NotNull @Valid StoreAdminRequestDto admin) {
}
