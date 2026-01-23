# Architectural Decisions

## Why Derived Ledger Balances?
Originally, the `Wallet` entity contained a bare-metal `BigDecimal balance` property. I discovered this anti-pattern is incredibly vulnerable to race conditions under load. I dropped the `balance` field and introduced a `ledger_entry` append-only entity. The wallet's true balance is now computed dynamically. It takes away the mutable state vector from the wallet itself, solving concurrent update collisions natively. 

## Pessimistic Over Optimistic Locking (For Transfers Only)
When two users rapid-fire request a transfer, I chose to use `LockModeType.PESSIMISTIC_WRITE` (`SELECT FOR UPDATE`) strictly on source wallets rather than generic `@Version` based optimistic locking. While optimistic locking prevents bad states, its default behavior under extreme load is throwing massive `OptimisticLockException` stacktraces causing HTTP 500s. I chose Pessimistic Locking to enforce sequential execution in the DB boundary itself immediately. 
*Note: I did NOT apply pessimistic locking to Add Funds (Deposits), as adding funds is purely append-only without precondition checks.*

## Idempotency Keys 
I added idempotency handling at the database level by marking `idempotency_key` as a `UNIQUE` index. This guarantees mathematical correctness. Previously, upstream retries by external API gateways could unintentionally process multiple transactions. Now, the `TransactionService` checks `findByIdempotencyKey` natively.

## Transaction State Machine
I hardened the `Status` enumerations to strictly follow: `PENDING` -> (`COMPLETED` or `FAILED`). Historically, it was named `SUCCESS` and `ERROR`. Expanding it to track deterministic lifecycles ensures that batch processors and alerting tools don't face ambiguous un-closed transactions. 

## Read Consistency
All querying operations (`findAll`, `getByIban`) are bound contextually by `Isolation.REPEATABLE_READ`. The connection will freeze the snapshot it's reading from, completely eliminating phantom or uncommitted transactional leaks.

## Redis Caching Strategy
I introduced a Redis-based caching layer for the distributed balance computations. To maintain a strict "Source of Truth" in the database, I chose **Cache Eviction on Write** rather than TTL-only. Whenever a transaction (Transfer/Deposit/Withdrawal) completes successfully, I evict the cached balance of the affected wallet(s). This guarantees that the next user-read is as fresh as the database ledger.

## Rate Limiting Constraints
To protect the system against high-frequency scripted attacks and database exhaustion, I implemented a per-user rate limiter using Redis `INCR` and `EXPIRE` primitives. The system currently enforces a threshold of 5 transfers per minute, returning a `429 Too Many Requests` status to maintain backend stability.

-- *Digital Wallet V3 - Correctness Focus*
