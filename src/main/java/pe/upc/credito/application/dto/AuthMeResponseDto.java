package pe.upc.credito.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record AuthMeResponseDto(
        Integer userId,
        String username,
        String email,
        String userType,
        @JsonInclude(JsonInclude.Include.ALWAYS) Integer storeId) {
}
