package pe.upc.credito.application.dto;

public record StoreResponseDto(
        Integer id,
        String business_name,
        String tax_id,
        String address,
        String phone_number,
        String created_at,
        String status) {
}
