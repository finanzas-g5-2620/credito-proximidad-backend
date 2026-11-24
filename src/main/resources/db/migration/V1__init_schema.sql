-- PostgreSQL baseline for a new Proximity Credit database.

CREATE TABLE stores (
    store_id       SERIAL          PRIMARY KEY,
    business_name  VARCHAR(100)    NOT NULL
                                   CHECK (char_length(business_name) BETWEEN 1 AND 100),
    tax_id         VARCHAR(20)     NOT NULL UNIQUE
                                   CHECK (tax_id ~ '^[0-9]{11}$'),
    address        VARCHAR(150)    NOT NULL,
    phone_number   VARCHAR(20),
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status         VARCHAR(20)     NOT NULL
                                   CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);
CREATE TABLE users (
    user_id        SERIAL          PRIMARY KEY,
    store_id       INT             REFERENCES stores(store_id),
    username       VARCHAR(50)     NOT NULL CHECK (position('@' in username) = 0),
    password_hash  VARCHAR(255)    NOT NULL,
    first_name     VARCHAR(100)    NOT NULL,
    last_name      VARCHAR(100)    NOT NULL,
    email          VARCHAR(100)    NOT NULL,
    user_type      VARCHAR(20)     NOT NULL
                                   CHECK (user_type IN ('SUPER_ADMIN', 'STORE_ADMIN', 'CUSTOMER')),
    status         VARCHAR(20)     NOT NULL
                                   CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED'))
);
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
                                         CHECK (compensatory_rate_type IN ('NOMINAL', 'EFFECTIVE')),
    compensatory_rate_period_days INT NOT NULL CHECK (compensatory_rate_period_days IN (1, 15, 30, 360)),
    compensatory_capitalization_days INT
        CHECK (compensatory_capitalization_days IN (1, 7, 15, 30, 60, 90, 180, 360)),
    compensatory_rate_value  DECIMAL(12,7) NOT NULL CHECK (compensatory_rate_value >= 0.0000000),
    penalty_rate_type        VARCHAR(10)  NOT NULL
                                         CHECK (penalty_rate_type IN ('NOMINAL', 'EFFECTIVE')),
    penalty_rate_period_days INT NOT NULL CHECK (penalty_rate_period_days IN (1, 15, 30, 360)),
    penalty_capitalization_days INT
        CHECK (penalty_capitalization_days IN (1, 7, 15, 30, 60, 90, 180, 360)),
    penalty_rate_value       DECIMAL(12,7) NOT NULL CHECK (penalty_rate_value >= 0.0000000),
    credit_currency          VARCHAR(3)   NOT NULL DEFAULT 'PEN' CHECK (credit_currency = 'PEN'),
    favour_balance           DECIMAL(12,2) NOT NULL DEFAULT 0.00 CHECK (favour_balance >= 0.00),
    CHECK ((compensatory_rate_type = 'NOMINAL') = (compensatory_capitalization_days IS NOT NULL)),
    CHECK ((penalty_rate_type = 'NOMINAL') = (penalty_capitalization_days IS NOT NULL))
);
CREATE TABLE products (
    product_id              SERIAL        PRIMARY KEY,
    store_id                INT           NOT NULL REFERENCES stores(store_id),
    name                    VARCHAR(100)  NOT NULL,
    description             TEXT,
    brand                   VARCHAR(50),
    supplier                VARCHAR(100),
    unit_of_measure         VARCHAR(20)   NOT NULL CHECK (unit_of_measure IN ('KG', 'LITER', 'UNIT', 'PACK', 'BAG')),
    image_url               VARCHAR(255),
    cash_price              DECIMAL(12,2) NOT NULL CHECK (cash_price > 0.00),
    credit_list_price       DECIMAL(12,2) NOT NULL CHECK (credit_list_price >= cash_price),
    allows_installments BOOLEAN       NOT NULL DEFAULT TRUE,
    current_stock           INT           NOT NULL CHECK (current_stock >= 0),
    min_stock               INT           NOT NULL CHECK (min_stock >= 0),
    status                  VARCHAR(20)   NOT NULL
                                          CHECK (status IN ('AVAILABLE', 'OUT_OF_STOCK', 'DISCONTINUED'))
);
CREATE TABLE sales (
    sale_id           SERIAL        PRIMARY KEY,
    store_id          INT           NOT NULL REFERENCES stores(store_id),
    customer_id       INT           NOT NULL REFERENCES customers(customer_id),
    sale_datetime     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_amount      DECIMAL(12,2) NOT NULL CHECK (total_amount > 0.00),
    payment_mode      VARCHAR(25)   NOT NULL
                                    CHECK (payment_mode IN ('CASH', 'MONTH_END', 'INSTALLMENTS')),
    number_of_months  INT           NOT NULL CHECK (number_of_months >= 1),
    tea               DECIMAL(12,7),
    penalty_tea       DECIMAL(12,7),
    settlement_id     INT,
    billing_cutoff_date DATE,
    first_due_date    DATE,
    idempotency_key   VARCHAR(100),
    request_fingerprint VARCHAR(64),
    sale_status       VARCHAR(20)   NOT NULL
                                    CHECK (sale_status IN ('REGISTERED', 'SCHEDULED', 'PAID', 'CANCELLED')),
    UNIQUE (store_id, idempotency_key)
);
CREATE TABLE sale_details (
    sale_detail_id  SERIAL        PRIMARY KEY,
    sale_id         INT           NOT NULL REFERENCES sales(sale_id) ON DELETE CASCADE,
    product_id      INT           NOT NULL REFERENCES products(product_id),
    quantity        INT           NOT NULL CHECK (quantity > 0),
    unit_price      DECIMAL(12,2) NOT NULL CHECK (unit_price > 0.00),
    subtotal        DECIMAL(12,2) NOT NULL CHECK (subtotal = quantity * unit_price)
);
CREATE TABLE amortization_schedules (
    schedule_id        SERIAL        PRIMARY KEY,
    sale_id            INT           NOT NULL REFERENCES sales(sale_id) ON DELETE RESTRICT,
    settlement_id      INT,
    installment_number INT           NOT NULL CHECK (installment_number >= 0),
    billing_cutoff_date DATE,
    due_date           DATE          NOT NULL,
    initial_balance    DECIMAL(12,2) NOT NULL CHECK (initial_balance >= 0.00),
    principal_amount   DECIMAL(12,2) NOT NULL CHECK (principal_amount >= 0.00),
    compensatory_interest DECIMAL(12,2) NOT NULL CHECK (compensatory_interest >= 0.00),
    fixed_installment  DECIMAL(12,2) NOT NULL CHECK (fixed_installment >= 0.00),
    ending_balance     DECIMAL(12,2) NOT NULL CHECK (ending_balance >= 0.00),
    grace_period_days  INT           NOT NULL DEFAULT 0 CHECK (grace_period_days >= 0),
    installment_status VARCHAR(20)   NOT NULL
                                      CHECK (installment_status IN ('PENDING', 'BILLED', 'PAID', 'OVERDUE'))
);
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
                                               CHECK (settlement_status IN ('PENDING', 'PAID', 'OVERDUE')),
    CONSTRAINT uq_settlement_period UNIQUE (customer_id, period_year, period_month)
);
ALTER TABLE amortization_schedules
    ADD CONSTRAINT fk_amortization_settlement
    FOREIGN KEY (settlement_id) REFERENCES monthly_settlements(settlement_id);
CREATE TABLE payments (
    payment_id               SERIAL        PRIMARY KEY,
    settlement_id            INT           NOT NULL REFERENCES monthly_settlements(settlement_id),
    payment_datetime         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    amount_paid              DECIMAL(12,2) NOT NULL CHECK (amount_paid > 0.00),
    calculated_overdue_days  INT           NOT NULL CHECK (calculated_overdue_days >= 0),
    allocated_to_penalty     DECIMAL(12,2) NOT NULL CHECK (allocated_to_penalty >= 0.00),
    allocated_to_interest    DECIMAL(12,2) NOT NULL CHECK (allocated_to_interest >= 0.00),
    allocated_to_principal   DECIMAL(12,2) NOT NULL CHECK (allocated_to_principal >= 0.00),
    payment_method           VARCHAR(50)   NOT NULL CHECK (payment_method IN ('CASH', 'BANK_TRANSFER', 'YAPE')),
    reference_number         VARCHAR(50),
    received_amount          DECIMAL(12,2) CHECK (received_amount >= amount_paid),
    favour_balance_after     DECIMAL(12,2) CHECK (favour_balance_after >= 0.00),
    idempotency_key          VARCHAR(100),
    UNIQUE (settlement_id, idempotency_key),
    CONSTRAINT ck_payment_imputation CHECK (
        amount_paid = allocated_to_penalty + allocated_to_interest + allocated_to_principal
    )
);
CREATE UNIQUE INDEX uq_users_username_normalized ON users(lower(username));
CREATE UNIQUE INDEX uq_users_email_normalized ON users(lower(email));
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


ALTER TABLE sales ADD CONSTRAINT fk_sale_settlement
    FOREIGN KEY (settlement_id) REFERENCES monthly_settlements(settlement_id);

CREATE INDEX idx_sales_settlement ON sales(settlement_id);
CREATE INDEX idx_unbilled_month_end_sale ON sales(customer_id, billing_cutoff_date)
    WHERE payment_mode = 'MONTH_END' AND settlement_id IS NULL;
CREATE INDEX idx_unbilled_installment ON amortization_schedules(billing_cutoff_date, sale_id)
    WHERE settlement_id IS NULL;

COMMENT ON COLUMN customers.compensatory_rate_value IS 'Agreed fraction for the reference period';
COMMENT ON COLUMN customers.penalty_rate_value IS 'Agreed fraction for the reference period';
COMMENT ON COLUMN payments.received_amount IS 'Tender received before excess is credited to the customer';
