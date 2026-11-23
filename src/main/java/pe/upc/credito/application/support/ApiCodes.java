package pe.upc.credito.application.support;

/**
 * Codigos legibles derivados de las llaves primarias. El capitulo 8 no
 * almacena columnas de codigo comercial; se generan de forma determinista
 * para que el frontend muestre comprobantes estables.
 */
public final class ApiCodes {

    private ApiCodes() {
    }

    public static String sale(Integer id) {
        return "V-%06d".formatted(id);
    }

    public static String settlement(Integer id) {
        return "L-%06d".formatted(id);
    }

    public static String payment(Integer id) {
        return "P-%06d".formatted(id);
    }

    public static String receipt(Integer id) {
        return "R-%06d".formatted(id);
    }

    /** DNI de 8 digitos derivado del customer_id (dato de presentacion). */
    public static String documentNumber(Integer customerId) {
        return "%08d".formatted(customerId);
    }

    /** SKU derivado del product_id (dato de presentacion). */
    public static String sku(Integer productId) {
        return "SKU-%04d".formatted(productId);
    }

    /** Categoria comercial derivada de la unidad de medida del producto. */
    public static String category(String unitOfMeasure) {
        if (unitOfMeasure == null) {
            return "General";
        }
        return switch (unitOfMeasure.toUpperCase()) {
            case "SACO", "CAJA", "PAQUETE", "LATA" -> "Abarrotes";
            case "BOTELLA", "LITRO" -> "Bebidas";
            case "KG", "KILOGRAMO" -> "Frescos";
            default -> "General";
        };
    }
}
