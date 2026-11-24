package pe.upc.credito.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Persisted catalog and inventory record for a store. */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Integer productId;

    @Column(name = "store_id", nullable = false)
    private Integer storeId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "brand", length = 50)
    private String brand;

    @Column(name = "supplier", length = 100)
    private String supplier;

    @Column(name = "unit_of_measure", nullable = false, length = 20)
    private String unitOfMeasure;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "cash_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal cashPrice;

    @Column(name = "credit_list_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal creditListPrice;

    @Column(name = "allows_installments", nullable = false)
    private Boolean allowsInstallments;

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    @Column(name = "min_stock", nullable = false)
    private Integer minStock;

    @Column(name = "status", nullable = false, length = 20)
    private String status;
}
