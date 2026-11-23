-- =====================================================================
-- V1__init_schema.sql
-- Esquema relacional inicial - Capitulo 8 "Modelo de Base de datos"
-- Fuente: 1ASI0642_Trabajo_7699_Grupo5, secciones 8.1, 8.2 y 8.3
-- Motor:   PostgreSQL 14+ (9.4.1)
--
-- Convenciones del diccionario formal (8.3):
--   * Llaves primarias: SERIAL (secuencial unico autoincremental)
--   * Dinero / flujos monetarios: DECIMAL(12, 2)  -> 2 decimales
--   * Tasas de interes:          DECIMAL(12, 7)  -> minimo 7 decimales
--   * Instantes de tiempo:       TIMESTAMPTZ (UTC)
--   * Fechas de calendario:      DATE
--   * Multi-tenant (Ley N. 29733): discriminador store_id en todas las
--     entidades de negocio, con aislamiento logico a nivel de fila.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Stores - Inquilino raiz (Tenant). Cap. 8.2 ficha 1 / Tabla 1.
-- ---------------------------------------------------------------------
CREATE TABLE stores (
    store_id       SERIAL          PRIMARY KEY,
    business_name  VARCHAR(100)    NOT NULL
                                   CHECK (char_length(business_name) BETWEEN 1 AND 100),
    tax_id         VARCHAR(20)     NOT NULL UNIQUE
                                   CHECK (tax_id ~ '^[0-9]{11}$'),   -- RUC peruano, 11 digitos
    address        VARCHAR(150)    NOT NULL,
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status         VARCHAR(20)     NOT NULL
                                   CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

COMMENT ON TABLE  stores IS 'Unidad comercial independiente; inquilino raiz de la plataforma';
COMMENT ON COLUMN stores.tax_id IS 'Registro Unico de Contribuyentes (RUC) - 11 digitos, unico';

-- ---------------------------------------------------------------------
-- 2. Users - IAM / RBAC. Cap. 8.2 ficha 2 / Tabla 2.
-- ---------------------------------------------------------------------
CREATE TABLE users (
    user_id        SERIAL          PRIMARY KEY,
    store_id       INT             REFERENCES stores(store_id),
                                     -- NULL unicamente para SUPERADMIN globales
    username       VARCHAR(50)     NOT NULL UNIQUE,
    password_hash  VARCHAR(255)    NOT NULL,   -- Argon2id / BCrypt, nunca texto claro
    first_name     VARCHAR(100)    NOT NULL,
    last_name      VARCHAR(100)    NOT NULL,
    email          VARCHAR(100)    NOT NULL UNIQUE,   -- RFC 5322
    user_type      VARCHAR(20)     NOT NULL
                                   CHECK (user_type IN ('SUPERADMIN', 'STOREADMIN', 'CUSTOMER')),
    status         VARCHAR(20)     NOT NULL
                                   CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED'))
);

COMMENT ON COLUMN users.store_id IS 'NULL solo para administradores globales de la plataforma';

-- ---------------------------------------------------------------------
-- 3. Customers - Cuenta corriente y expediente de riesgo. Cap. 8.2 ficha 3 / Tabla 3.
-- ---------------------------------------------------------------------
CREATE TABLE customers (
    customer_id              SERIAL       PRIMARY KEY,
    store_id                 INT          NOT NULL REFERENCES stores(store_id),
    user_id                  INT          NOT NULL UNIQUE REFERENCES users(user_id),
    home_address             VARCHAR(150) NOT NULL,
    phone_number             VARCHAR(20)  NOT NULL,
    credit_limit             DECIMAL(12,2) NOT NULL CHECK (credit_limit >= 0.00),
    max_credit_months        INT          NOT NULL CHECK (max_credit_months BETWEEN 1 AND 36),
    cutoff_day               INT          NOT NULL CHECK (cutoff_day BETWEEN 1 AND 30),
    payment_due_day          INT          NOT NULL CHECK (payment_due_day BETWEEN 1 AND 30),
    compensatory_rate_type   VARCHAR(10)  NOT NULL
                                         CHECK (compensatory_rate_type IN ('NOMINAL', 'EFECTIVA')),
    compensatory_rate_value  DECIMAL(12,7) NOT NULL CHECK (compensatory_rate_value >= 0.0000000),
    penalty_rate_type        VARCHAR(10)  NOT NULL
                                         CHECK (penalty_rate_type IN ('NOMINAL', 'EFECTIVA')),
    penalty_rate_value       DECIMAL(12,7) NOT NULL CHECK (penalty_rate_value >= 0.0000000),
    credit_currency          VARCHAR(3)   NOT NULL DEFAULT 'PEN',
    favour_balance           DECIMAL(12,2) NOT NULL DEFAULT 0.00 CHECK (favour_balance >= 0.00)
);

COMMENT ON COLUMN customers.compensatory_rate_value IS
    'Tasa compensatoria anualizada, 7 decimales. Tope: BCRP (Art. 1243 C.C. / Ley N. 31143)';
COMMENT ON COLUMN customers.penalty_rate_value IS
    'Tasa moratoria anualizada, 7 decimales. Tope: BCRP (Art. 1243 C.C. / Ley N. 31143)';
COMMENT ON COLUMN customers.favour_balance IS 'Saldo a favor del deudor por pagos excedentes';

-- ---------------------------------------------------------------------
-- 4. Products - Inventario del comercio. Cap. 8.2 ficha 4 / Tabla 4.
-- ---------------------------------------------------------------------
CREATE TABLE products (
    product_id              SERIAL        PRIMARY KEY,
    store_id                INT           NOT NULL REFERENCES stores(store_id),
    name                    VARCHAR(100)  NOT NULL,
    description             TEXT,
    brand                   VARCHAR(50),
    supplier                VARCHAR(100),
    unit_of_measure         VARCHAR(20)   NOT NULL,  -- 'KG', 'LITRO', 'UNIDAD', 'PAQUETE'
    image_url               VARCHAR(255),
    cash_price              DECIMAL(12,2) NOT NULL CHECK (cash_price > 0.00),
    credit_list_price       DECIMAL(12,2) NOT NULL CHECK (credit_list_price >= cash_price),
    allows_monthly_payments BOOLEAN       NOT NULL DEFAULT TRUE,
    current_stock           INT           NOT NULL CHECK (current_stock >= 0),
    min_stock               INT           NOT NULL CHECK (min_stock >= 0),
    status                  VARCHAR(20)   NOT NULL
                                          CHECK (status IN ('AVAILABLE', 'OUT_OF_STOCK', 'DISCONTINUED'))
);

-- ---------------------------------------------------------------------
-- 5. Sales - Encabezado transaccional. Cap. 8.2 ficha 5 / Tabla 5.
-- ---------------------------------------------------------------------
CREATE TABLE sales (
    sale_id           SERIAL        PRIMARY KEY,
    store_id          INT           NOT NULL REFERENCES stores(store_id),
    customer_id       INT           NOT NULL REFERENCES customers(customer_id),
    sale_datetime     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_amount      DECIMAL(12,2) NOT NULL CHECK (total_amount > 0.00),
    payment_mode      VARCHAR(25)   NOT NULL
                                    CHECK (payment_mode IN ('CONTADO', 'FINANCIADO_MENSUAL', 'FINANCIADO_CUOTAS')),
    number_of_months  INT           NOT NULL CHECK (number_of_months >= 1),
    sale_status       VARCHAR(20)   NOT NULL
                                    CHECK (sale_status IN ('REGISTRADA', 'EN_CRONOGRAMA', 'CANCELADA', 'ANULADA'))
);

COMMENT ON COLUMN sales.total_amount IS
    'Debe ser identico a la sumatoria de SaleDetails.subtotal (regla 8.2, ficha 5)';

-- ---------------------------------------------------------------------
-- 6. SaleDetails - Detalle de mercancias. Cap. 8.2 ficha 6 / Tabla 6.
-- ---------------------------------------------------------------------
CREATE TABLE sale_details (
    sale_detail_id  SERIAL        PRIMARY KEY,
    sale_id         INT           NOT NULL REFERENCES sales(sale_id) ON DELETE CASCADE,
    product_id      INT           NOT NULL REFERENCES products(product_id),
    quantity        INT           NOT NULL CHECK (quantity > 0),
    unit_price      DECIMAL(12,2) NOT NULL CHECK (unit_price > 0.00),
    subtotal        DECIMAL(12,2) NOT NULL CHECK (subtotal = quantity * unit_price)
);

COMMENT ON COLUMN sale_details.subtotal IS 'Invariante: subtotal = quantity * unit_price';

-- ---------------------------------------------------------------------
-- 7. AmortizationSchedules - Cronograma frances, base comercial 360.
--    Cap. 8.2 ficha 7 / Tabla 7.
-- ---------------------------------------------------------------------
CREATE TABLE amortization_schedules (
    schedule_id        SERIAL        PRIMARY KEY,
    sale_id            INT           NOT NULL REFERENCES sales(sale_id) ON DELETE RESTRICT,
    settlement_id      INT,                -- FK a monthly_settlements; NULL hasta el ciclo de corte
    installment_number INT           NOT NULL CHECK (installment_number >= 0),  -- 0 = periodo de gracia
    due_date           DATE          NOT NULL,
    initial_balance    DECIMAL(12,2) NOT NULL CHECK (initial_balance >= 0.00),
    principal_amount   DECIMAL(12,2) NOT NULL CHECK (principal_amount >= 0.00),
    compensatory_interest DECIMAL(12,2) NOT NULL CHECK (compensatory_interest >= 0.00),
    fixed_installment  DECIMAL(12,2) NOT NULL CHECK (fixed_installment >= 0.00),
    ending_balance     DECIMAL(12,2) NOT NULL CHECK (ending_balance >= 0.00),
    grace_period_days  INT           NOT NULL DEFAULT 0 CHECK (grace_period_days >= 0),
    installment_status VARCHAR(20)   NOT NULL
                                      CHECK (installment_status IN ('PENDIENTE', 'FACTURADA', 'PAGADA', 'EN_MORA'))
);

COMMENT ON COLUMN amortization_schedules.fixed_installment IS
    'Invariante: fixed_installment = principal_amount + compensatory_interest';
COMMENT ON COLUMN amortization_schedules.ending_balance IS
    'En la ultima cuota (installment_number = n) debe ser 0.00 - absorbe el residuo de redondeo';

-- ---------------------------------------------------------------------
-- 8. MonthlySettlements - Estado de cuenta mensual. Cap. 8.2 ficha 8 / Tabla 8.
-- ---------------------------------------------------------------------
CREATE TABLE monthly_settlements (
    settlement_id                SERIAL        PRIMARY KEY,
    customer_id                  INT           NOT NULL REFERENCES customers(customer_id),
    period_month                 INT           NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    period_year                  INT           NOT NULL CHECK (period_year >= 2026),
    cutoff_date                  DATE          NOT NULL,
    payment_due_date             DATE          NOT NULL CHECK (payment_due_date >= cutoff_date),
    month_purchases_amount       DECIMAL(12,2) NOT NULL DEFAULT 0.00 CHECK (month_purchases_amount >= 0.00),
    month_installments_amount    DECIMAL(12,2) NOT NULL DEFAULT 0.00 CHECK (month_installments_amount >= 0.00),
    compensatory_interest_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 CHECK (compensatory_interest_amount >= 0.00),
    penalty_interest_amount      DECIMAL(12,2) NOT NULL DEFAULT 0.00 CHECK (penalty_interest_amount >= 0.00),
    total_amount_due             DECIMAL(12,2) NOT NULL,
    paid_amount                  DECIMAL(12,2) NOT NULL DEFAULT 0.00 CHECK (paid_amount >= 0.00),
    pending_balance              DECIMAL(12,2) NOT NULL CHECK (pending_balance >= 0.00),
    settlement_status            VARCHAR(20)   NOT NULL
                                               CHECK (settlement_status IN ('EMITIDA', 'PAGADA_TOTAL', 'EN_MORA')),
    -- Un solo estado de cuenta por cliente y periodo comercial
    CONSTRAINT uq_settlement_period UNIQUE (customer_id, period_year, period_month)
);

COMMENT ON COLUMN monthly_settlements.total_amount_due IS
    'total_amount_due = month_purchases + month_installments + compensatory_interest + penalty_interest';
COMMENT ON COLUMN monthly_settlements.pending_balance IS
    'Al emitir: pending_balance = total_amount_due y paid_amount = 0.00. Pasa a PAGADA_TOTAL solo si = 0.00';

-- Llave foranea diferida: amortization_schedules se crea antes que
-- monthly_settlements (se respeto el orden del diccionario 8.3).
ALTER TABLE amortization_schedules
    ADD CONSTRAINT fk_amortization_settlement
    FOREIGN KEY (settlement_id) REFERENCES monthly_settlements(settlement_id);

-- ---------------------------------------------------------------------
-- 9. Payments - Cobros en ventanilla. Cap. 8.2 ficha 9 / Tabla 9.
--    Prelacion legal Art. 1257 del Codigo Civil.
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    payment_id               SERIAL        PRIMARY KEY,
    settlement_id            INT           NOT NULL REFERENCES monthly_settlements(settlement_id),
    payment_datetime         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    amount_paid              DECIMAL(12,2) NOT NULL CHECK (amount_paid > 0.00),
    calculated_overdue_days  INT           NOT NULL CHECK (calculated_overdue_days >= 0),
    allocated_to_penalty     DECIMAL(12,2) NOT NULL CHECK (allocated_to_penalty >= 0.00),
    allocated_to_interest    DECIMAL(12,2) NOT NULL CHECK (allocated_to_interest >= 0.00),
    allocated_to_principal   DECIMAL(12,2) NOT NULL CHECK (allocated_to_principal >= 0.00),
    payment_method           VARCHAR(50)   NOT NULL,
    -- Invariante de prelacion (Art. 1257 C.C.):
    --   amount_paid = mora + interes compensatorio + capital
    CONSTRAINT ck_payment_imputation CHECK (
        amount_paid = allocated_to_penalty + allocated_to_interest + allocated_to_principal
    )
);

COMMENT ON COLUMN payments.allocated_to_penalty   IS 'Nivel 1 de prelacion: abono a mora';
COMMENT ON COLUMN payments.allocated_to_interest  IS 'Nivel 2 de prelacion: abono a interes compensatorio';
COMMENT ON COLUMN payments.allocated_to_principal IS 'Nivel 3 de prelacion: amortizacion al capital';

-- =====================================================================
-- Indices de apoyo a las llaves foraneas (rendimiento de JOINs y
-- consultas multi-tenant filtradas por store_id)
-- =====================================================================
CREATE INDEX idx_users_store             ON users(store_id);
CREATE INDEX idx_customers_store         ON customers(store_id);
CREATE INDEX idx_products_store          ON products(store_id);
CREATE INDEX idx_sales_store             ON sales(store_id);
CREATE INDEX idx_sales_customer          ON sales(customer_id);
CREATE INDEX idx_sale_details_sale       ON sale_details(sale_id);
CREATE INDEX idx_sale_details_product    ON sale_details(product_id);
CREATE INDEX idx_amortization_sale       ON amortization_schedules(sale_id);
CREATE INDEX idx_amortization_settlement ON amortization_schedules(settlement_id);
CREATE INDEX idx_settlements_customer    ON monthly_settlements(customer_id);
CREATE INDEX idx_payments_settlement     ON payments(settlement_id);

-- =====================================================================
-- NOTA (pendiente, fuera del diagrama del cap. 8):
-- La matriz de trazabilidad (8.4) exige validar las tasas de Clients
-- contra "tablas de topes legales del BCRP vigentes". Dichas tablas no
-- aparecen en el diagrama ni en el diccionario; se agregaran en una
-- migracion posterior cuando se implemente la validacion de tasas.
-- =====================================================================
