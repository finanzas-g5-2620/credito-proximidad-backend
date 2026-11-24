-- Optional fixture for the local Docker demo database.
-- Adds a standalone statement for the synthetic store 1 customer.
INSERT INTO monthly_settlements (
    customer_id, period_month, period_year, cutoff_date, payment_due_date,
    month_purchases_amount, total_amount_due, pending_balance, settlement_status
)
SELECT c.customer_id, 11, 2026, DATE '2026-11-20', DATE '2026-11-30',
       42.50, 42.50, 42.50, 'PENDING'
FROM customers c
WHERE c.customer_id = 2 AND c.store_id = 1
  AND c.cutoff_day = 20 AND c.payment_due_day = 30
ON CONFLICT (customer_id, period_year, period_month) DO NOTHING
RETURNING settlement_id, customer_id, pending_balance, settlement_status;
