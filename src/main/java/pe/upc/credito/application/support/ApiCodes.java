package pe.upc.credito.application.support;

/** Derives stable identifiers and catalog categories from stored records. */
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

    /** Synthetic eight-digit display identifier derived from the customer ID. */
    public static String documentNumber(Integer customerId) {
        return "%08d".formatted(customerId);
    }

    /** SKU derived from the product ID. */
    public static String sku(Integer productId) {
        return "SKU-%04d".formatted(productId);
    }

    /** Catalog category derived from the product unit. */
    public static String category(String unitOfMeasure) {
        if (unitOfMeasure == null) {
            return "GENERAL";
        }
        return switch (unitOfMeasure.toUpperCase()) {
            case "BAG", "PACK" -> "GROCERIES";
            case "LITER" -> "BEVERAGES";
            case "KG" -> "FRESH";
            default -> "GENERAL";
        };
    }
}
