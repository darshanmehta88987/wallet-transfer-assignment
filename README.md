# Wallet Transfer Service

A reliable wallet-to-wallet transfer service that demonstrates safe handling of
idempotency, concurrency, ledger consistency, and transfer state transitions.

> Submission for the Robustrade `wallet-transfer-assignment`.

---

## Quick start

```powershell
# 1. Start Postgres (any way you like; e.g. docker compose, RDS, local install)
docker run --name wallet-pg -p 5432:5432 `
  -e POSTGRES_DB=wallet -e POSTGRES_USER=wallet -e POSTGRES_PASSWORD=wallet `
  -d postgres:16-alpine

# 2. Build and run
.\gradlew.bat bootRun
# → service listens on http://localhost:8080
```

The service requires Java 17+ and a Postgres reachable at the URL configured in
`application.yml` (defaults to `jdbc:postgresql://localhost:5432/wallet`,
overridable via `SPRING_DATASOURCE_*` env vars). Flyway runs on startup and
creates the schema.

To exercise the API you'll need a few seeded wallets. The simplest path is to
`INSERT INTO wallets (id, balance) VALUES (...)` directly; for example:

```sql
INSERT INTO wallets (id, balance) VALUES
  ('wallet-a', 10000),
  ('wallet-b', 0);
```

---

## API

### `POST /transfers`

```json
{
  "idempotencyKey": "abc123",
  "fromWalletId": "wallet-a",
  "toWalletId":   "wallet-b",
  "amount": 100
}
```

Responses:

| Status | When |
|--------|------|
| 201 Created             | First successful execution: transfer PROCESSED |
| 200 OK                  | Idempotent replay — same key, same payload — original body returned |
| 422 Unprocessable Entity | First execution, insufficient funds (transfer persisted as FAILED, replayable) |
| 400 Bad Request          | Validation error or self-transfer |
| 404 Not Found            | Source or destination wallet does not exist |
| 409 Conflict             | Same idempotency key reused with a different payload |
| 500 Internal Server Error | Unexpected failure |

Successful body:

```json
{
  "transferId": "33333333-3333-3333-3333-333333333333",
  "status": "PROCESSED",
  "fromWalletId": "...",
  "toWalletId": "...",
  "amount": 100,
  "createdAt": "2026-05-21T10:15:30Z"
}
```

Error envelope:

```json
{ "error": "WALLET_NOT_FOUND", "message": "wallet not found: ..." }
```

### Sample `curl` walkthrough

```powershell
# Happy path — returns 201
curl -X POST http://localhost:8080/transfers `
  -H "Content-Type: application/json" `
  -d '{"idempotencyKey":"k1","fromWalletId":"wallet-a","toWalletId":"wallet-b","amount":100}'

# Replay — same key, same payload — returns 200 with identical body
curl -X POST http://localhost:8080/transfers `
  -H "Content-Type: application/json" `
  -d '{"idempotencyKey":"k1","fromWalletId":"wallet-a","toWalletId":"wallet-b","amount":100}'

# Conflict — same key, different amount — returns 409
curl -X POST http://localhost:8080/transfers `
  -H "Content-Type: application/json" `
  -d '{"idempotencyKey":"k1","fromWalletId":"wallet-a","toWalletId":"wallet-b","amount":200}'

# Insufficient funds — first time, returns 422 with FAILED status
curl -X POST http://localhost:8080/transfers `
  -H "Content-Type: application/json" `
  -d '{"idempotencyKey":"k2","fromWalletId":"wallet-a","toWalletId":"wallet-b","amount":999999}'
```

---

## Design highlights

The full design rationale is in [`approach.md`](./approach.md). A condensed
view:

- **Single DB transaction per request** at `READ_COMMITTED`. The transfer row,
  both wallet updates, both ledger entries, and the idempotency record commit
  together or roll back together.
- **Pessimistic row locking** on wallets via `SELECT ... FOR UPDATE`
  (Hibernate `PESSIMISTIC_WRITE` on Postgres). Wallets are locked in ascending
  string id order so reverse-direction transfers acquire the same locks in the same order
  to prevent deadlocks.
- **Structural guarantees** at the storage layer that are independent of
  application logic:
  - `CHECK (balance >= 0)` on `wallets` — cannot double-spend even with a buggy
    service.
  - `UNIQUE (transfer_id, type)` on `ledger_entries` — a transfer can produce at
    most one DEBIT and one CREDIT row.
  - `CHECK (from_wallet_id <> to_wallet_id)` on `transfers`.
- **Idempotency** via a dedicated `idempotency_records` table that stores the
  request hash plus the cached response body. Replays return the cached body
  byte-for-byte with HTTP 200. A reused key with a different payload returns
  409.

---

## Running the tests

```powershell
# Unit + service + entity tests (no Docker needed)
.\gradlew.bat test

# Integration + concurrency tests against a real Postgres (requires Docker for Testcontainers)
.\gradlew.bat integrationTest

# Everything
.\gradlew.bat check
```

### Test inventory

| Suite | Count | What it covers |
|-------|-------|----------------|
| `entity/TransferTest`             | 8 | State machine: invalid transitions, validation |
| `entity/WalletTest`               | 5 | Debit/credit invariants, overdraft, validation |
| `entity/LedgerEntryTest`          | 3 | Signed amount, constructor invariants |
| `service/RequestHasherTest`       | 3 | SHA-256 determinism and field sensitivity |
| `controller/TransferControllerIT` | 8 | Happy path, replay (same payload), 409 conflict, 422 + replay, 400 self-transfer, 404 wallet not found, validation 400 |
| `integration/ConcurrencyIT`       | 4 | **C1** no double-spend (50 threads, 30 funded), **C2** 100-thread duplicate collapse, **C3** deadlock-free A↔B, **C4** mixed workload invariants |
| **Total**                         | **31** | |

All 31 tests pass against Postgres 16 (Testcontainers) on a fresh checkout.

---

## Repository layout

```
src/main/java/com/kullu/wallet/
├── WalletApplication.java
├── controller/         # @RestController + @RestControllerAdvice
├── service/            # @Transactional orchestration (TransferService)
├── repository/         # Spring Data JPA repos, FOR UPDATE on wallets
├── entity/             # JPA entities with domain-level state transitions
├── dto/{request,response}/
└── exception/          # Domain exceptions

src/main/resources/
├── application.yml
└── db/migration/V1__init.sql

src/test/java/                       # fast tests (no Docker)
src/integrationTest/java/            # Testcontainers Postgres tests
```

---

## See also

- [`ASSIGNMENT.md`](./ASSIGNMENT.md) — original prompt
- [`approach.md`](./approach.md) — design note (schema, idempotency, concurrency, tradeoffs)
- [`AI_TRANSCRIPT.md`](./AI_TRANSCRIPT.md) — disclosure of AI usage during the build
