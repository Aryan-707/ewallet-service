CREATE SEQUENCE IF NOT EXISTS ledger_entry_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ledger_entry
(
    id               BIGINT                      NOT NULL,
    wallet_id        BIGINT                      NOT NULL,
    type             VARCHAR(10)                 NOT NULL,
    amount           DECIMAL                     NOT NULL,
    transaction_id   BIGINT                      NOT NULL,
    created_at       TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT pk_ledger_entry PRIMARY KEY (id)
);

ALTER TABLE ledger_entry
    ADD CONSTRAINT FK_LEDGER_ENTRY_ON_TRANSACTION FOREIGN KEY (transaction_id) REFERENCES transaction (id);

ALTER TABLE ledger_entry
    ADD CONSTRAINT FK_LEDGER_ENTRY_ON_WALLET FOREIGN KEY (wallet_id) REFERENCES wallet (id);

-- Migration index requirements
CREATE INDEX idx_ledger_wallet_id ON ledger_entry(wallet_id);

-- Adding idempotency_key to transaction table
ALTER TABLE transaction ADD COLUMN idempotency_key VARCHAR(100);
ALTER TABLE transaction ADD CONSTRAINT uc_transaction_idempotency_key UNIQUE (idempotency_key);
CREATE INDEX idx_transaction_idempotency ON transaction(idempotency_key);

-- Remove balance field from wallet
ALTER TABLE wallet DROP COLUMN balance;
