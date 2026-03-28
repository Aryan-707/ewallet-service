-- =============================================================
-- V8 – Seed rich demo data for the recruiter account
-- Balances are derived from ledger_entry rows (no balance column on wallet).
-- Recruiter user was created in V7 with nextval; we look it up by username.
-- =============================================================

DO $$
DECLARE
  rec_user_id   BIGINT;
  john_id       BIGINT := 1;   -- johndoe from V2
  linda_id      BIGINT := 2;   -- lindacalvin from V2

  rec_wallet1   BIGINT;        -- Recruiter: Main Wallet (USD)
  rec_wallet2   BIGINT;        -- Recruiter: Savings Wallet
  john_wallet   BIGINT;        -- johndoe's existing wallet (for cross-user transfers)

  t1 BIGINT; t2 BIGINT; t3 BIGINT; t4 BIGINT; t5 BIGINT;
  t6 BIGINT; t7 BIGINT; t8 BIGINT; t9 BIGINT; t10 BIGINT;
  le1 BIGINT; le2 BIGINT;
BEGIN

  -- ── look up recruiter ────────────────────────────────────────
  SELECT id INTO rec_user_id FROM public."user" WHERE username = 'recruiter';

  -- skip entirely if demo data already seeded (idempotency guard)
  IF EXISTS (SELECT 1 FROM wallet WHERE user_id = rec_user_id) THEN
    RAISE NOTICE 'Demo data already seeded – skipping V8.';
    RETURN;
  END IF;

  -- ── create a wallet for johndoe (peer for transfers) ─────────
  -- Only create if he doesn't already have one
  IF NOT EXISTS (SELECT 1 FROM wallet WHERE user_id = john_id) THEN
    SELECT nextval('wallet_seq') INTO john_wallet;
    INSERT INTO wallet (id, iban, name, user_id)
    VALUES (john_wallet,
            'GB29NWBK60161331926822',
            'John Main Wallet',
            john_id);
  ELSE
    SELECT id INTO john_wallet FROM wallet WHERE user_id = john_id LIMIT 1;
  END IF;

  -- ── recruiter wallets ─────────────────────────────────────────
  SELECT nextval('wallet_seq') INTO rec_wallet1;
  INSERT INTO wallet (id, iban, name, user_id)
  VALUES (rec_wallet1,
          'DE89370400440532013000',
          'Main Wallet',
          rec_user_id);

  SELECT nextval('wallet_seq') INTO rec_wallet2;
  INSERT INTO wallet (id, iban, name, user_id)
  VALUES (rec_wallet2,
          'FR7630006000011234567890189',
          'Savings Wallet',
          rec_user_id);

  -- ═══════════════════════════════════════════════════════════════
  -- TRANSACTION 1 – Initial deposit into Main Wallet ($2 500)
  -- (self-funding: from_wallet = to_wallet is allowed for DEPOSIT)
  -- ═══════════════════════════════════════════════════════════════
  SELECT nextval('transaction_seq') INTO t1;
  INSERT INTO transaction (id, amount, description, created_at, reference_number,
                           status, from_wallet_id, to_wallet_id, type_id, idempotency_key)
  VALUES (t1, 2500.00, 'Initial deposit',
          NOW() - INTERVAL '30 days',
          gen_random_uuid(), 'COMPLETED',
          rec_wallet1, rec_wallet1, 2,
          'demo-t1-' || rec_user_id);

  SELECT nextval('ledger_entry_seq') INTO le1;
  INSERT INTO ledger_entry (id, wallet_id, type, amount, transaction_id, created_at)
  VALUES (le1, rec_wallet1, 'CREDIT', 2500.00, t1, NOW() - INTERVAL '30 days');

  -- ═══════════════════════════════════════════════════════════════
  -- TRANSACTION 2 – Initial deposit into Savings Wallet ($1 000)
  -- ═══════════════════════════════════════════════════════════════
  SELECT nextval('transaction_seq') INTO t2;
  INSERT INTO transaction (id, amount, description, created_at, reference_number,
                           status, from_wallet_id, to_wallet_id, type_id, idempotency_key)
  VALUES (t2, 1000.00, 'Savings deposit',
          NOW() - INTERVAL '29 days',
          gen_random_uuid(), 'COMPLETED',
          rec_wallet2, rec_wallet2, 2,
          'demo-t2-' || rec_user_id);

  SELECT nextval('ledger_entry_seq') INTO le2;
  INSERT INTO ledger_entry (id, wallet_id, type, amount, transaction_id, created_at)
  VALUES (le2, rec_wallet2, 'CREDIT', 1000.00, t2, NOW() - INTERVAL '29 days');

  -- ═══════════════════════════════════════════════════════════════
  -- TRANSACTION 3 – Recruiter pays a bill (Payment, -$120)
  -- ═══════════════════════════════════════════════════════════════
  SELECT nextval('transaction_seq') INTO t3;
  INSERT INTO transaction (id, amount, description, created_at, reference_number,
                           status, from_wallet_id, to_wallet_id, type_id, idempotency_key)
  VALUES (t3, 120.00, 'Electricity bill',
          NOW() - INTERVAL '25 days',
          gen_random_uuid(), 'COMPLETED',
          rec_wallet1, rec_wallet1, 2,
          'demo-t3-' || rec_user_id);

  SELECT nextval('ledger_entry_seq') INTO le1;
  INSERT INTO ledger_entry (id, wallet_id, type, amount, transaction_id, created_at)
  VALUES (le1, rec_wallet1, 'DEBIT', 120.00, t3, NOW() - INTERVAL '25 days');

  -- ═══════════════════════════════════════════════════════════════
  -- TRANSACTION 4 – Shopping ($85)
  -- ═══════════════════════════════════════════════════════════════
  SELECT nextval('transaction_seq') INTO t4;
  INSERT INTO transaction (id, amount, description, created_at, reference_number,
                           status, from_wallet_id, to_wallet_id, type_id, idempotency_key)
  VALUES (t4, 85.00, 'Online shopping',
          NOW() - INTERVAL '20 days',
          gen_random_uuid(), 'COMPLETED',
          rec_wallet1, rec_wallet1, 3,
          'demo-t4-' || rec_user_id);

  SELECT nextval('ledger_entry_seq') INTO le1;
  INSERT INTO ledger_entry (id, wallet_id, type, amount, transaction_id, created_at)
  VALUES (le1, rec_wallet1, 'DEBIT', 85.00, t4, NOW() - INTERVAL '20 days');

  -- ═══════════════════════════════════════════════════════════════
  -- TRANSACTION 5 – Transfer from Main → Savings ($300)
  -- ═══════════════════════════════════════════════════════════════
  SELECT nextval('transaction_seq') INTO t5;
  INSERT INTO transaction (id, amount, description, created_at, reference_number,
                           status, from_wallet_id, to_wallet_id, type_id, idempotency_key)
  VALUES (t5, 300.00, 'Move to savings',
          NOW() - INTERVAL '15 days',
          gen_random_uuid(), 'COMPLETED',
          rec_wallet1, rec_wallet2, 1,
          'demo-t5-' || rec_user_id);

  SELECT nextval('ledger_entry_seq') INTO le1;
  INSERT INTO ledger_entry (id, wallet_id, type, amount, transaction_id, created_at)
  VALUES (le1, rec_wallet1, 'DEBIT', 300.00, t5, NOW() - INTERVAL '15 days');

  SELECT nextval('ledger_entry_seq') INTO le2;
  INSERT INTO ledger_entry (id, wallet_id, type, amount, transaction_id, created_at)
  VALUES (le2, rec_wallet2, 'CREDIT', 300.00, t5, NOW() - INTERVAL '15 days');

  -- ═══════════════════════════════════════════════════════════════
  -- TRANSACTION 6 – Transfer from John → Recruiter ($500)
  -- ═══════════════════════════════════════════════════════════════
  -- John wallet needs some funds first (seed ledger credit)
  SELECT nextval('transaction_seq') INTO t6;
  INSERT INTO transaction (id, amount, description, created_at, reference_number,
                           status, from_wallet_id, to_wallet_id, type_id, idempotency_key)
  VALUES (t6, 5000.00, 'John seed deposit',
          NOW() - INTERVAL '31 days',
          gen_random_uuid(), 'COMPLETED',
          john_wallet, john_wallet, 2,
          'demo-t6-john-seed');

  SELECT nextval('ledger_entry_seq') INTO le1;
  INSERT INTO ledger_entry (id, wallet_id, type, amount, transaction_id, created_at)
  VALUES (le1, john_wallet, 'CREDIT', 5000.00, t6, NOW() - INTERVAL '31 days');

  -- Now John sends $500 to recruiter Main Wallet
  SELECT nextval('transaction_seq') INTO t7;
  INSERT INTO transaction (id, amount, description, created_at, reference_number,
                           status, from_wallet_id, to_wallet_id, type_id, idempotency_key)
  VALUES (t7, 500.00, 'Refund from John',
          NOW() - INTERVAL '12 days',
          gen_random_uuid(), 'COMPLETED',
          john_wallet, rec_wallet1, 1,
          'demo-t7-' || rec_user_id);

  SELECT nextval('ledger_entry_seq') INTO le1;
  INSERT INTO ledger_entry (id, wallet_id, type, amount, transaction_id, created_at)
  VALUES (le1, john_wallet, 'DEBIT', 500.00, t7, NOW() - INTERVAL '12 days');

  SELECT nextval('ledger_entry_seq') INTO le2;
  INSERT INTO ledger_entry (id, wallet_id, type, amount, transaction_id, created_at)
  VALUES (le2, rec_wallet1, 'CREDIT', 500.00, t7, NOW() - INTERVAL '12 days');

  -- ═══════════════════════════════════════════════════════════════
  -- TRANSACTION 8 – Recruiter pays $250 to John
  -- ═══════════════════════════════════════════════════════════════
  SELECT nextval('transaction_seq') INTO t8;
  INSERT INTO transaction (id, amount, description, created_at, reference_number,
                           status, from_wallet_id, to_wallet_id, type_id, idempotency_key)
  VALUES (t8, 250.00, 'Dinner payment',
          NOW() - INTERVAL '8 days',
          gen_random_uuid(), 'COMPLETED',
          rec_wallet1, john_wallet, 2,
          'demo-t8-' || rec_user_id);

  SELECT nextval('ledger_entry_seq') INTO le1;
  INSERT INTO ledger_entry (id, wallet_id, type, amount, transaction_id, created_at)
  VALUES (le1, rec_wallet1, 'DEBIT', 250.00, t8, NOW() - INTERVAL '8 days');

  SELECT nextval('ledger_entry_seq') INTO le2;
  INSERT INTO ledger_entry (id, wallet_id, type, amount, transaction_id, created_at)
  VALUES (le2, john_wallet, 'CREDIT', 250.00, t8, NOW() - INTERVAL '8 days');

  -- ═══════════════════════════════════════════════════════════════
  -- TRANSACTION 9 – Shopping from Savings ($45)
  -- ═══════════════════════════════════════════════════════════════
  SELECT nextval('transaction_seq') INTO t9;
  INSERT INTO transaction (id, amount, description, created_at, reference_number,
                           status, from_wallet_id, to_wallet_id, type_id, idempotency_key)
  VALUES (t9, 45.00, 'Streaming services',
          NOW() - INTERVAL '5 days',
          gen_random_uuid(), 'COMPLETED',
          rec_wallet2, rec_wallet2, 3,
          'demo-t9-' || rec_user_id);

  SELECT nextval('ledger_entry_seq') INTO le1;
  INSERT INTO ledger_entry (id, wallet_id, type, amount, transaction_id, created_at)
  VALUES (le1, rec_wallet2, 'DEBIT', 45.00, t9, NOW() - INTERVAL '5 days');

  -- ═══════════════════════════════════════════════════════════════
  -- TRANSACTION 10 – Most recent: incoming payment ($150) from Linda
  -- ═══════════════════════════════════════════════════════════════
  -- Seed Linda wallet first
  IF NOT EXISTS (SELECT 1 FROM wallet WHERE user_id = linda_id) THEN
    SELECT nextval('wallet_seq') INTO john_wallet; -- reuse variable
    INSERT INTO wallet (id, iban, name, user_id)
    VALUES (john_wallet, 'ES9121000418450200051332', 'Linda Main Wallet', linda_id);

    SELECT nextval('transaction_seq') INTO t10;
    INSERT INTO transaction (id, amount, description, created_at, reference_number,
                             status, from_wallet_id, to_wallet_id, type_id, idempotency_key)
    VALUES (t10, 3000.00, 'Linda seed deposit',
            NOW() - INTERVAL '31 days',
            gen_random_uuid(), 'COMPLETED',
            john_wallet, john_wallet, 2,
            'demo-t10-linda-seed');

    SELECT nextval('ledger_entry_seq') INTO le1;
    INSERT INTO ledger_entry (id, wallet_id, type, amount, transaction_id, created_at)
    VALUES (le1, john_wallet, 'CREDIT', 3000.00, t10, NOW() - INTERVAL '31 days');
  ELSE
    SELECT id INTO john_wallet FROM wallet WHERE user_id = linda_id LIMIT 1;
  END IF;

  SELECT nextval('transaction_seq') INTO t10;
  INSERT INTO transaction (id, amount, description, created_at, reference_number,
                           status, from_wallet_id, to_wallet_id, type_id, idempotency_key)
  VALUES (t10, 150.00, 'Linda pays back',
          NOW() - INTERVAL '1 day',
          gen_random_uuid(), 'COMPLETED',
          john_wallet, rec_wallet1, 1,
          'demo-t10-' || rec_user_id);

  SELECT nextval('ledger_entry_seq') INTO le1;
  INSERT INTO ledger_entry (id, wallet_id, type, amount, transaction_id, created_at)
  VALUES (le1, john_wallet, 'DEBIT', 150.00, t10, NOW() - INTERVAL '1 day');

  SELECT nextval('ledger_entry_seq') INTO le2;
  INSERT INTO ledger_entry (id, wallet_id, type, amount, transaction_id, created_at)
  VALUES (le2, rec_wallet1, 'CREDIT', 150.00, t10, NOW() - INTERVAL '1 day');

  -- ── reset sequences ───────────────────────────────────────────
  PERFORM setval('wallet_seq',      (SELECT MAX(id) FROM wallet));
  PERFORM setval('transaction_seq', (SELECT MAX(id) FROM transaction));
  PERFORM setval('ledger_entry_seq',(SELECT MAX(id) FROM ledger_entry));

  RAISE NOTICE 'V8 demo seed complete. Recruiter user_id = %', rec_user_id;
END $$;
