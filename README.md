# E-Wallet

Built to explore concurrency-safe financial systems using a
ledger-first design.

A digital wallet backend built to handle concurrent financial
transactions without double-spending, lost updates, or phantom reads.

The core architectural decision was to eliminate the mutable balance
column entirely. Instead of storing a running total that multiple
threads can corrupt simultaneously, every financial operation appends
immutable rows to a ledger table. Balance is always computed as
SUM(CREDIT) - SUM(DEBIT).

## What this solves

Standard wallet implementations store balance as a single number:

sql
UPDATE wallets SET balance = balance - 200 WHERE id = 1


Under concurrent load, two threads read the same balance, both
believe they have sufficient funds, both decrement, and one
transaction effectively disappears. This is a lost update.

This project eliminates that class of bug at the schema level by
making balance a derived value, not a stored one.

## Architecture


User Request
   │
   ▼
[ JWT Filter ] — stateless auth, any node can verify
   │
   ▼
[ Rate Limiter ] — Redis INCR/EXPIRE, 5 transfers/min per user
   │
   ▼
[ Ownership Check ] — verifies authenticated user owns source wallet
   │
   ▼
[ Idempotency Check ] — rejects duplicate requests before DB touch
   │
   ▼
[ Pessimistic Lock ] — SELECT FOR UPDATE on source wallet
   │
   ▼
[ Balance Validation ] — computed from ledger SUM, not a column
   │
   ▼
[ Ledger Write ] — DEBIT on source + CREDIT on destination (atomic)
   │
   ▼
[ Transaction Record ] — status: PENDING → COMPLETED
   │
   ▼
[ Cache Eviction ] — invalidates Redis balance cache for both wallets
   │
   ▼
[ Event Published ] — TransactionCompletedEvent (AFTER_COMMIT)
   │
   ▼
[ Async Audit ] — @Async listener logs to audit trail, non-blocking


<!-- INSERT LEDGER DIAGRAM HERE -->
![Ledger Model](docs/images/ledger-diagram.png)

## Concurrency failure modes

| Scenario | Without this system | With this system |
|---|---|---|
| Two concurrent transfers | Lost update, balance corrupted | Pessimistic lock serializes writes |
| Mobile app retry | Duplicate transaction, double spend | Idempotency key blocks replay |
| Redis cache stale after write | Reads incorrect balance | Explicit eviction after every write |
| App crash mid-transfer | Partial ledger write possible | @Transactional rolls back atomically |

## Core engineering decisions

**Append-only ledger instead of mutable balance**
Balance is never stored. It is computed via:

sql
SELECT COALESCE(SUM(CASE WHEN type = 'CREDIT' THEN amount
                         ELSE -amount END), 0)
FROM ledger_entry WHERE wallet_id = :id


This eliminates the lost update problem at the schema level.
No UPDATE on a balance column means no race condition on that column.

This design trades write-time simplicity and correctness for
read-time cost. Balance queries require aggregation, which becomes
expensive at scale without snapshotting.

**Pessimistic locking on source wallet only**
SELECT FOR UPDATE via @Lock(LockModeType.PESSIMISTIC_WRITE) is
applied only to the source wallet during transfers and withdrawals.
Deposits are append-only and require no lock — they have no
precondition beyond wallet existence.

Optimistic locking was avoided because high contention on hot
wallets would lead to frequent retries and degraded latency under
load. Pessimistic locking serializes writes upfront rather than
paying the retry cost after the fact.

**Idempotency at the database level**
Every transaction carries an idempotency_key with a UNIQUE
constraint. Before processing, the service queries for an existing
key. If found, it returns the existing result without touching the
ledger. This handles mobile app retries and API gateway replays
without double-spending.

**Redis cache-through with explicit eviction**
Balance queries check Redis first. On cache miss, the ledger SUM
runs against PostgreSQL and the result is cached with a 1-hour TTL.
After every write, the cache for affected wallets is explicitly
evicted — not left to expire — ensuring the next read is always fresh.

**Ownership verification before any wallet operation**
Every wallet access extracts the authenticated username from
SecurityContextHolder and compares it against the wallet owner.
A mismatch throws AccessDeniedException (403) before any business
logic runs. This prevents horizontal privilege escalation where
a valid user accesses another user's wallet by guessing IDs.

**Async audit logging decoupled from transaction**
A TransactionCompletedEvent is published after commit
(TransactionPhase.AFTER_COMMIT). An @Async listener handles audit
logging outside the main transaction boundary. If the audit fails,
the transaction is unaffected — audit is observability, not
correctness.

## Concurrency model

The system defends against concurrent access at three levels:

Layer 1 — Redis rate limiter prevents burst abuse before any DB
interaction.

Layer 2 — Pessimistic lock on the source wallet serializes competing
withdrawals and transfers at the row level. Only one thread holds
the lock at a time.

Layer 3 — Ledger append semantics mean even if two writes somehow
reach the DB simultaneously, the result is two immutable rows, not
a corrupted balance scalar. The SUM query always reflects reality.

## Testing


WalletServiceConcurrencyTest  — 10 threads compete for $100
                                balance with $20 transfers.
                                Asserts exactly 5 succeed,
                                5 fail, final balance = $0.

WalletIntegrationTest         — hits real H2 database.
                                Verifies ledger math, idempotency
                                constraint, and insufficient
                                balance rollback end-to-end.

WalletCacheTest               — verifies Redis cache-through
                                and eviction behavior.


Load tests were executed using a multi-threaded JUnit simulation.
Results are indicative benchmarks, not production measurements.
Raw output in metrics/load_test_results.md.

## Known limitations

**Ledger SUM query scales poorly without snapshots**
Computing balance from full ledger history is correct but slow
for wallets with millions of transactions. The fix is a periodic
balance snapshot table with incremental ledger computation from
the last snapshot. Not implemented — documented as the natural
next step.

**Single PostgreSQL node**
No read replicas. Write throughput ceiling is approximately
5000 TPS on a standard cloud instance. Horizontal read scaling
would require replica routing via @Transactional(readOnly=true).

**No refresh token mechanism**
JWT expires in 1 hour with no renewal flow. The user must
re-authenticate. Acceptable for v1 — production would require
a refresh token with rotation.

**Dashboard stats are real but not real-time**
Wallet, user, and transaction counts are fetched from the API
on page load. They do not update without a refresh. WebSocket
or SSE would be required for live counters.

**Single transaction boundary**
This system guarantees correctness within a single database
transaction. Cross-service consistency — for example, coordinating
with an external payment provider — would require a Saga pattern
or compensating transactions. That boundary is not addressed here.

## Running locally

bash
# Start PostgreSQL
docker compose up -d

# Start Redis
docker run -d -p 6379:6379 redis

# Set environment variables
export DB_URL="jdbc:postgresql://localhost:5432/ewallet_db"
export DB_USERNAME="postgres"
export DB_PASSWORD="yourpassword"
export JWT_SECRET="your-256-bit-secret-minimum-32-chars"

# Build and run backend
cd backend
mvn clean install -DskipTests
java -jar target/e-wallet-0.0.1-SNAPSHOT.jar

# Run frontend
cd frontend
npm install
npm start


API docs: http://localhost:8080/swagger-ui.html
Frontend: http://localhost:3000

## Load test results

500 concurrent threads targeting the same wallet simultaneously.

| Metric | Naive balance column | Ledger + pessimistic lock |
|---|---|---|
| P99 latency | 4500ms | 120ms |
| Throughput | 150 TPS | ~4000 TPS |
| Error rate | 12% | 0% |
| Double spends | Present | Zero |

