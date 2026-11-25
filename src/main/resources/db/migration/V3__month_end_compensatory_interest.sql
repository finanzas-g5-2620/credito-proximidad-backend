ALTER TABLE sales
    ADD COLUMN month_end_interest DECIMAL(12,2) NOT NULL DEFAULT 0.00
    CHECK (month_end_interest >= 0.00);
