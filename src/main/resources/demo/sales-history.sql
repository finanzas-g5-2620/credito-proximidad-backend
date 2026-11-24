-- Local demo fixture. Historical entries do not change current stock.
WITH months AS (
    SELECT period::date AS month, row_number() OVER (ORDER BY period) AS sequence
    FROM generate_series(
        date_trunc('month', now() AT TIME ZONE 'America/Lima') - interval '11 months',
        date_trunc('month', now() AT TIME ZONE 'America/Lima') - interval '1 month',
        interval '1 month'
    ) AS periods(period)
), fixtures AS (
    SELECT m.*, c.customer_id, c.store_id, p.product_id, p.cash_price, p.credit_list_price
    FROM months m
    JOIN users u ON u.username IN ('maria.quispe', 'carlos.ruiz')
    JOIN customers c ON c.user_id = u.user_id AND c.store_id = u.store_id
    AND NOT EXISTS (
        SELECT 1 FROM sales WHERE store_id = c.store_id AND idempotency_key LIKE 'demo-history-%'
    )
    CROSS JOIN LATERAL (
        SELECT product_id, cash_price, credit_list_price
        FROM products WHERE store_id = c.store_id AND status = 'AVAILABLE'
        ORDER BY product_id LIMIT 1
    ) p
), statements AS (
    INSERT INTO monthly_settlements (
        customer_id, period_month, period_year, cutoff_date, payment_due_date,
        month_purchases_amount, total_amount_due, paid_amount, pending_balance, settlement_status
    )
    SELECT customer_id, extract(month FROM month), extract(year FROM month), month + 24,
           (month + interval '1 month')::date + 4,
           credit_list_price * (1 + sequence % 4), credit_list_price * (1 + sequence % 4),
           credit_list_price * (1 + sequence % 4), 0, 'PAID'
    FROM fixtures
    WHERE month >= DATE '2026-01-01'
      AND (month + interval '1 month')::date + 4 <= (now() AT TIME ZONE 'America/Lima')::date
    ON CONFLICT (customer_id, period_year, period_month) DO NOTHING
    RETURNING *
), sale_fixtures AS (
    SELECT f.*, NULL::integer AS settlement_id, NULL::date AS cutoff_date,
           NULL::date AS due_date, 'CASH' AS mode, 'REGISTERED' AS sale_status,
           cash_price AS unit_price, (2 + sequence % 5)::integer AS quantity
    FROM fixtures f
    UNION ALL
    SELECT f.*, s.settlement_id, s.cutoff_date, s.payment_due_date, 'MONTH_END',
           'PAID', credit_list_price, (1 + sequence % 4)::integer
    FROM fixtures f JOIN statements s ON s.customer_id = f.customer_id AND s.cutoff_date = f.month + 24
), inserted_sales AS (
    INSERT INTO sales (
        store_id, customer_id, sale_datetime, total_amount, payment_mode, number_of_months,
        settlement_id, billing_cutoff_date, first_due_date, idempotency_key, sale_status
    )
    SELECT store_id, customer_id, (month + 10 + time '12:00') AT TIME ZONE 'America/Lima',
           unit_price * quantity, mode, 1, settlement_id, cutoff_date, due_date,
           'demo-history-' || month || '-' || mode, sale_status
    FROM sale_fixtures
    ON CONFLICT (store_id, idempotency_key) DO NOTHING
    RETURNING sale_id, store_id, idempotency_key
), details AS (
    INSERT INTO sale_details (sale_id, product_id, quantity, unit_price, subtotal)
    SELECT s.sale_id, f.product_id, f.quantity, f.unit_price, f.quantity * f.unit_price
    FROM inserted_sales s
    JOIN sale_fixtures f ON s.store_id = f.store_id AND s.idempotency_key = 'demo-history-' || f.month || '-' || f.mode
    RETURNING sale_id
), receipts AS (
    INSERT INTO payments (
        settlement_id, payment_datetime, amount_paid, calculated_overdue_days,
        allocated_to_penalty, allocated_to_interest, allocated_to_principal,
        payment_method, received_amount, idempotency_key
    )
    SELECT settlement_id, (payment_due_date + time '12:00') AT TIME ZONE 'America/Lima',
           paid_amount, 0, 0, 0, paid_amount, 'CASH', paid_amount, 'demo-history-payment'
    FROM statements
    RETURNING payment_id
)
SELECT (SELECT count(*) FROM details) AS sales_added,
       (SELECT count(*) FROM receipts) AS payments_added;
