# Digital Wallet Ledger System

A fintech-grade robust digital wallet service designed to process high-throughput concurrent financial transactions with absolute data integrity.

## Core Features
1. **Append-Only Ledger**: Balances are calculated dynamically (`SUM(credits) - SUM(debits)`) through deeply indexed mathematical sequences. We do not trust basic `balance` scalar fields anymore.
2. **Deterministic Idempotency**: All transfer endpoints require an `X-Idempotency-Key` which prevents double spending and replay-attacks natively in the Postgres constraints.
3. **Pessimistic State Boundaries**: High contention transfers lock records asynchronously using `SELECT FOR UPDATE` patterns to strictly prevent Phantom Reads.
4. **Resilient Transaction Fallback**: Financial state cannot be 'stranded'. Ledger statuses rigorously bounce between `PENDING` to `COMPLETED` or roll back cleanly to `FAILED` with zero side effects.
5. **Redis-Based Read Optimization**: High-frequency balance lookups are offloaded to an elastic Redis memory layer with intelligent Write-Through Invalidation.
6. **User-Level Rate Limiting**: Transfer endpoints are protected from brute-force and DDoS via sliding-window Redis counters (5 req/min).

## Technologies Used
* **Java 17** & **Spring Boot 3.0**
* **Spring Data JPA / Hibernate**
* **Spring Data Redis**
* **PostgreSQL** + **Flyway**
* **MapStruct** & **Lombok**
* **Swagger UI / SpringDoc OpenAPI 2.5** 
* **JUnit 5 / Mockito**
* **Docker** (Redis + Postgres)

## Local Development and Running
Ensure you have Docker, PostgreSQL, and Maven.

1. Install Dependencies:
`mvn clean install`
2. Configure `.env` properties for Database (refer to `application.yml` structure) or simply export `db_username` and `db_password` and `jwt_secret`.
3. Stand up Postgres Database (e.g. `docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=password postgres`).
4. Run locally:
`java -jar backend/target/e-wallet-0.0.1-SNAPSHOT.jar`

## API Details
- **Swagger Documentation**: Accessible at `/swagger-ui.html` locally. Contains interactive mapping for `/api/v1/transactions` and `/api/v1/wallets`.
- **Pagination Strategy**: Standardised across collection endpoints to enforce backend memory safeguards.

## Testing Standards
Run tests via:
`mvn test`
The suite contains `WalletServiceConcurrencyTest` demonstrating the architectural resilience logic under simultaneous HTTP calls and threaded blocking parameters.
