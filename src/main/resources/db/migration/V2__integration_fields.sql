-- =====================================================================
-- V2__integration_fields.sql
-- Campos adicionales requeridos por el contrato del frontend Angular.
--
-- El capitulo 8 define el modelo nucleo. Estos dos campos no alteran
-- ninguna tabla, relacion ni regla del diccionario 8.3: solo persisten
-- datos que el frontend ya envia en sus DTOs y que, de otro modo, se
-- perderian.
--
--   * sales.tea               TEA efectiva pactada para la venta (el
--                             frontend permite una tasa distinta a la
--                             tasa compensatoria del cliente).
--   * payments.reference_number  Nro. de operacion de YAPE/transferencia.
-- =====================================================================

ALTER TABLE sales
    ADD COLUMN tea DECIMAL(12, 7);

COMMENT ON COLUMN sales.tea IS
    'TEA efectiva pactada para la venta; NULL en ventas al contado';

ALTER TABLE payments
    ADD COLUMN reference_number VARCHAR(50);

COMMENT ON COLUMN payments.reference_number IS
    'Referencia externa del medio de pago (YAPE / transferencia)';
