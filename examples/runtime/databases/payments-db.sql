-- Schema of payments-db (PostgreSQL in production; SQLite-compatible DDL for the demo).
-- Loaded into an in-memory database and read back through SQL Schema Context.

CREATE TABLE customers (
    id INTEGER PRIMARY KEY,
    email TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE payments (
    id INTEGER PRIMARY KEY,
    customer_id INTEGER NOT NULL,
    amount_cents INTEGER NOT NULL,
    status TEXT NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE INDEX idx_payments_status ON payments(status);

-- No index on payment_id: refund history lookups scan the whole table.
CREATE TABLE payment_transactions (
    id INTEGER PRIMARY KEY,
    payment_id INTEGER NOT NULL,
    card_token TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE TABLE refunds (
    id INTEGER PRIMARY KEY,
    payment_id INTEGER NOT NULL,
    amount_cents INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE TABLE ledger_entries (
    id INTEGER PRIMARY KEY,
    payment_id INTEGER NOT NULL,
    account TEXT NOT NULL,
    amount_cents INTEGER NOT NULL,
    FOREIGN KEY (payment_id) REFERENCES payments(id)
);
