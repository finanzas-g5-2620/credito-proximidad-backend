package pe.upc.credito.application.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StorePatchRequestDto(
        @Size(max = 100) String business_name,
        @Pattern(regexp = "^[0-9]{11}$", message = "Tax ID must contain 11 digits") String tax_id,
        @Size(max = 150) String address,
        @Size(max = 20) String phone_number,
        @Pattern(regexp = "^(ACTIVE|INACTIVE|SUSPENDED)$") String status) {
}
