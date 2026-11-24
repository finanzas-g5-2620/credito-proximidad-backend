WITH samples(name, stock, minimum, status, allows_installments) AS (
    VALUES ('Jabón de manos 250 ml', 2, 8, 'AVAILABLE', true),
           ('Papel toalla doble hoja', 0, 6, 'OUT_OF_STOCK', true),
           ('Paquete promocional de galletas', 0, 4, 'DISCONTINUED', false),
           ('Agua mineral 625 ml', 36, 8, 'AVAILABLE', false)
)
INSERT INTO products (
    store_id, name, description, brand, supplier, unit_of_measure, cash_price,
    credit_list_price, allows_installments, current_stock, min_stock, status
)
SELECT s.store_id, sample.name, 'Producto de demostración', 'Selección local',
       'Distribuidora Central', 'UNIT', 4.50, 5.00, sample.allows_installments,
       sample.stock, sample.minimum, sample.status
FROM stores s CROSS JOIN samples sample
WHERE s.tax_id IN ('20512345678', '20698765432')
AND NOT EXISTS (
    SELECT 1 FROM products p WHERE p.store_id = s.store_id AND p.name = sample.name
);

WITH business_date AS (
    SELECT (now() AT TIME ZONE 'America/Lima')::date AS today
), candidates AS (
    SELECT c.customer_id, c.store_id, u.username, p.product_id, p.credit_list_price,
           period::date + (c.cutoff_day - 1) AS cutoff_date,
           least(due_month.start_date + (c.payment_due_day - 1),
                 (due_month.start_date + interval '1 month')::date - 1) AS due_date,
           d.today
    FROM customers c
    JOIN users u ON u.user_id = c.user_id
    CROSS JOIN business_date d
    CROSS JOIN LATERAL generate_series(
        date_trunc('month', d.today::timestamp) - interval '2 months',
        date_trunc('month', d.today::timestamp), interval '1 month'
    ) periods(period)
    CROSS JOIN LATERAL (
        SELECT (period + CASE WHEN c.payment_due_day <= c.cutoff_day THEN interval '1 month'
                              ELSE interval '0 months' END)::date AS start_date
    ) due_month
    CROSS JOIN LATERAL (
        SELECT product_id, credit_list_price FROM products
        WHERE store_id = c.store_id AND status = 'AVAILABLE'
        ORDER BY credit_list_price, product_id LIMIT 1
    ) p
    WHERE u.username IN ('jose.ramirez', 'ana.torres')
    AND NOT EXISTS (
        SELECT 1 FROM sales s WHERE s.store_id = c.store_id
        AND s.idempotency_key = 'demo-collection-' || u.username
    )
), fixtures AS (
    SELECT DISTINCT ON (customer_id) *
    FROM candidates WHERE cutoff_date < today
    AND cutoff_date >= DATE '2026-01-01'
    AND (username <> 'ana.torres' OR due_date < today)
    ORDER BY customer_id, cutoff_date DESC
), statements AS (
    INSERT INTO monthly_settlements (
        customer_id, period_month, period_year, cutoff_date, payment_due_date,
        month_purchases_amount, total_amount_due, pending_balance, settlement_status
    )
    SELECT customer_id, extract(month FROM cutoff_date), extract(year FROM cutoff_date),
           cutoff_date, due_date, 8 * credit_list_price, 8 * credit_list_price,
           8 * credit_list_price, CASE WHEN due_date < today THEN 'OVERDUE' ELSE 'PENDING' END
    FROM fixtures
    ON CONFLICT (customer_id, period_year, period_month) DO NOTHING
    RETURNING settlement_id, customer_id, cutoff_date, payment_due_date
), historical_sales AS (
    INSERT INTO sales (
        store_id, customer_id, sale_datetime, total_amount, payment_mode, number_of_months,
        settlement_id, billing_cutoff_date, first_due_date, idempotency_key, sale_status, penalty_tea
    )
    SELECT f.store_id, f.customer_id,
           (s.cutoff_date - 5 + time '12:00') AT TIME ZONE 'America/Lima',
           8 * f.credit_list_price, 'MONTH_END', 1, s.settlement_id, s.cutoff_date,
           s.payment_due_date, 'demo-collection-' || f.username, 'SCHEDULED',
           CASE WHEN f.username = 'ana.torres' THEN 0.10 ELSE 0.15 END
    FROM statements s JOIN fixtures f USING (customer_id)
    RETURNING sale_id, customer_id
)
INSERT INTO sale_details (sale_id, product_id, quantity, unit_price, subtotal)
SELECT s.sale_id, f.product_id, 8, f.credit_list_price, 8 * f.credit_list_price
FROM historical_sales s JOIN fixtures f USING (customer_id);

-- Repair earlier copies of this fixture without changing user-created sales.
UPDATE sales
SET penalty_tea = CASE WHEN idempotency_key = 'demo-collection-ana.torres' THEN 0.10 ELSE 0.15 END
WHERE idempotency_key IN ('demo-collection-ana.torres', 'demo-collection-jose.ramirez')
  AND penalty_tea IS NULL;
