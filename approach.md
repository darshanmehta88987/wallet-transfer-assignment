# Wallet Transfer Service — Approach & Design Note

- Status: **Draft / Pre-implementation**
- Author: Darshan Mehta

This document captures my understanding of the assignment and the design I plan to implement, before writing any code. It will be updated as implementation progresses and finalised in the PR description.

---

## 1. Problem Statement

Build a service exposing `POST /transfers` that moves an amount from one wallet to another with following requirements:

- **Idempotent** — If the same idempotencyKey is used again, the system must return the original result. Duplicate requests must not trigger duplicate transfers.
- **Double-entry ledger** — every successful transfer produces exactly one DEBIT row on the source wallet and one CREDIT row on the destination wallet. The ledger must always balance.
- **Transfer States** — transfers move `PENDING → PROCESSED` or `PENDING → FAILED`; transitions must be safe under retry.
- **Concurrency-safe** — The system must ensure: correct balances, no double spending.
- **Cleanly layered** — controller / service / repository / entity

---

## 2. Assumptions & Scope

- Amount represented as `BIGINT` minor units (e.g. paise / cents) — avoids floating-point rounding.
- Single currency. Wallets are pre-seeded; there is no wallet-creation API in scope.
- Authentication, authorisation, and multi-tenancy are out of scope.
- Self-transfer (`fromWalletId == toWalletId`) is rejected with HTTP 400.
- Idempotency key: opaque string, max 64 characters, retained indefinitely (no TTL).

---

## 3. API Contract

**Endpoint**

```
POST /transfers
```

**Request body**

```json
{
  "idempotencyKey": "abc123",
  "fromWalletId": "wallet_1",
  "toWalletId": "wallet_2",
  "amount": 100
}
```

Field constraints, enforced via Bean Validation:

- `idempotencyKey` — `@NotBlank`, `@Size(max = 64)`
- `fromWalletId`, `toWalletId` — `@NotNull`
- `amount` — `@NotNull`, `@Positive`

**Responses**

- `201 Created` — first successful execution of a key
- `200 OK` — idempotent replay returns the cached response

Response body:

```json
{
  "transferId": "…",
  "status": "PROCESSED",
  "fromWalletId": "wallet_1",
  "toWalletId": "wallet_2",
  "amount": 100,
  "createdAt": "2026-05-21T10:15:30Z"
}
```

**Error matrix**

| Status | Condition |
|--------|-----------|
| 400    | Validation failure or self-transfer |
| 404    | Source or destination wallet not found |
| 409    | Same idempotency key reused with a different payload, or duplicate is still in flight |
| 422    | Insufficient funds — transfer is persisted as `FAILED` |
| 500    | Unexpected error |

Error envelope: `{ "error": "<code>", "message": "<human readable>" }`.

---

## 4. Database Schema (Postgres + Flyway)

**`wallets`**

- `id UUID PRIMARY KEY`
- `balance BIGINT NOT NULL CHECK (balance >= 0)`
- `created_at`, `updated_at TIMESTAMPTZ`

**`transfers`**

- `id UUID PRIMARY KEY`
- `from_wallet_id UUID NOT NULL REFERENCES wallets(id)`
- `to_wallet_id   UUID NOT NULL REFERENCES wallets(id)`
- `amount BIGINT NOT NULL CHECK (amount > 0)`
- `status VARCHAR NOT NULL CHECK (status IN ('PENDING','PROCESSED','FAILED'))`
- `failure_reason VARCHAR NULL`
- `created_at`, `updated_at TIMESTAMPTZ`
- `CHECK (from_wallet_id <> to_wallet_id)`

**`ledger_entries`**

- `id UUID PRIMARY KEY`
- `wallet_id UUID NOT NULL REFERENCES wallets(id)`
- `transfer_id UUID NOT NULL REFERENCES transfers(id)`
- `type VARCHAR NOT NULL CHECK (type IN ('DEBIT','CREDIT'))`
- `amount BIGINT NOT NULL CHECK (amount > 0)`
- `created_at TIMESTAMPTZ`
- `UNIQUE (transfer_id, type)` — guarantees exactly one DEBIT and one CREDIT per transfer

**`idempotency_records`**

- `key VARCHAR PRIMARY KEY`
- `request_hash VARCHAR NOT NULL`
- `transfer_id UUID NOT NULL REFERENCES transfers(id)`
- `response_status SMALLINT`
- `response_body JSONB`
- `created_at TIMESTAMPTZ`

---

## 5. Idempotency Strategy

- `request_hash` = SHA-256 over `(fromWalletId, toWalletId, amount)`.
- The transactional flow begins with:

  ```sql
  INSERT INTO idempotency_records (key, request_hash, transfer_id, …)
  VALUES (…)
  ON CONFLICT (key) DO NOTHING;
  ```

- **Insert succeeded** → first time we are seeing this key. Execute the transfer, then `UPDATE` the record with `response_status` and `response_body`.
- **Insert conflicted** → load the existing record:
  - matching `request_hash` and stored response present → return the cached response.
  - matching `request_hash` but response not yet stored → duplicate is still in flight → respond `409`.
  - mismatching `request_hash` → key reuse with a different payload → respond `409`.
- The idempotency record is written in the **same** DB transaction as the transfer and ledger entries, so we cannot end up with a transfer that has no idempotency record (or vice versa).

---

## 6. Concurrency Strategy

**Approach**

- Single `@Transactional(isolation = READ_COMMITTED)` service method.
- `SELECT … FOR UPDATE` on both wallet rows, acquired in deterministic ascending order of wallet `id`. This eliminates deadlocks for reverse-direction concurrent transfers (`A→B` racing `B→A`).
- Within the lock window: funds check → balance updates → 2 ledger inserts → transfer status update → idempotency record finalisation. All atomic.
- DB-level `CHECK (balance >= 0)` acts as a hard safety net independent of application logic — even a bug in the service layer cannot drive a wallet negative.
- Pessimistic locking chosen over optimistic

**Concurrency tests**

Executed against Testcontainers Postgres or h2 db with parallel threads coordinated through `ExecutorService` + `CountDownLatch`:

| ID  | Invariant under test |
|-----|----------------------|
| C1  | **No double spend.** 50 threads attempt to debit the same wallet; the wallet is funded for exactly 30 of them. Exactly 30 transfers reach `PROCESSED` and 20 reach `FAILED` with `INSUFFICIENT_FUNDS`. Final balance is zero; the balance is never observed negative by any concurrent reader. |
| C2  | **Idempotency under parallel duplicates.** 100 threads submit the same `idempotencyKey` simultaneously. Exactly 1 row in `transfers` and exactly 2 rows in `ledger_entries` are created, and all 100 HTTP responses are byte-identical. |
| C3  | **Deadlock-free bidirectional load.** Symmetric concurrent transfers `A→B` and `B→A` across many threads complete without deadlock. Per-wallet net change equals the algebraic sum of applied transfers; the ledger remains balanced. |
| C4  | **Mixed workload invariants.** 80 threads split 40/40 between replays of a single key and unique transfers. Asserts (a) ledger zero-sum across all entries, and (b) for every wallet `balance == initial_balance + Σ(credits) − Σ(debits)` computed from the ledger. |

Every test asserts both the externally visible outcome (HTTP status counts, response bodies) and the persisted invariants (row counts, ledger sums, balance equation).

---

## 7. Transfer State Machine

- A `transfers` row is inserted in state `PENDING` at the beginning of the transaction.
- Transitions:
  - `PENDING → PROCESSED` on a successful debit/credit pair.
  - `PENDING → FAILED` (with `failure_reason`) when funds are insufficient.
- Because the entire flow runs inside one DB transaction, `PENDING` is transient on disk for the happy path — but it is still persisted in the row so that future asynchronous workflows (e.g. external clearing) can extend the model without schema changes.
- Retries reuse the idempotency key and therefore never mutate a row that has already reached a terminal state.

---

## 8. Failure Modes

- **Validation failure** → no DB writes.
- **Wallet not found** → 404, no writes.
- **Insufficient funds** → transfer row written as `FAILED`, no ledger entries, idempotency record stored so retries return the same `FAILED` response.
- **DB constraint violation** → mapped by the exception handler to 409 or 422 as appropriate.
- **Process crash mid-transaction** → Postgres rolls back; a client retry with the same key replays cleanly.
- **Response lost in flight** → client retry hits the idempotency cache and receives the original response (200).

---

## 9. Architecture / Layering

Package layout mirrors the structure used in my Coras project:

```
com.kullu.wallet
├── controller          // @RestController, request handling, status mapping
├── service             // @Service, @Transactional boundary, idempotency + transfer orchestration
├── jpa.repository      // Spring Data JPA repositories, @Lock(PESSIMISTIC_WRITE) queries
├── entity              // JPA entities (Wallet, Transfer, LedgerEntry, IdempotencyRecord)
├── dto.request         // inbound DTOs with Bean Validation annotations
├── dto.response        // outbound DTOs
├── exception           // domain exceptions + @RestControllerAdvice handler
└── configs             // Spring configuration (datasource, JPA, Jackson)
```

Layering rules:

- Controllers stay thin — validate, delegate, map exceptions to HTTP status. No business logic.
- The service layer owns the `@Transactional` boundary and is the only place that orchestrates idempotency together with transfer execution.
- Repositories expose only persistence operations; no business decisions.
- Entities carry their own state-transition methods (`markProcessed()`, `markFailed(reason)`) so invalid transitions are impossible at the domain level.
- JPA entities never cross the controller boundary — request and response DTOs sit at the edges.

---

## 10. Testing Strategy

- **Unit tests** (JUnit 5 + Mockito): state machine transitions, validation, request-hash computation and equality.
- **Repository slice tests** (`@DataJpaTest`): unique idempotency key, unique `(transfer_id, type)` on ledger entries, `CHECK` constraints on balance and amount.
- **Integration tests** with Testcontainers Postgres or h2 db:
  - happy path — balances updated and exactly 2 ledger entries written, summing to zero.
  - duplicate key with the same payload → identical response, no extra rows.
  - duplicate key with a different payload → 409.
  - insufficient funds → `FAILED`, balances unchanged.
  - self-transfer rejected.
- Discipline: **Red → Green → Refactor** — failing test first, smallest correct implementation, then refactor under a green suite.

---

## 11. Tradeoffs & Non-Goals

- **Stored balance** chosen over deriving from the ledger — fast reads, simple insufficient-funds check; the ledger remains the audit trail. A reconciliation job is out of scope.
- **Pessimistic locking** chosen over optimistic locking (justification in §6).
- **Synchronous single-transaction workflow** chosen over an async PENDING + worker design — simpler to reason about, and the schema leaves room to evolve later.
- No outbox, event publishing, FX, fees, holds, reservations, rate limiting, or auth.
- Observability, balance API, and transfer history API are deferred — they belong to the assignment's optional enhancements section.
