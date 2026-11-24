package pe.upc.credito.application.dto;

import java.math.BigDecimal;

public record ProductResponseDto(
        Integer id,
        Integer store_id,
        String name,
        String description,
        String brand,
        String supplier,
        String unit_of_measure,
        String image_url,
        BigDecimal cash_price,
        BigDecimal credit_list_price,
        Boolean allows_installments,
        Integer current_stock,
        Integer min_stock,
        String status) {
}
