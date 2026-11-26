# 0002. Automation reads and writes the Store through its own ports

- Status: accepted
- Date: 2026-11-26

## Context

`sequeless-automation` must not depend on `sequeless-core` or any Store module (ADR 0001 and
sequeless-prototype#20, point 5). Before this decision, the automation code read objects through the
Store's `ObjectStorePort` and wrote them through `ObjectStorePort.commit` and `TransitionService`. Those
types would carry the Store into this repo, and the write path built the Store's own change set and outbox
row.

Two questions followed. What does automation read and write, and who builds the Store-side payloads for a
write it issues?

Decided in the Navigator sessions recorded in `docs/extraction/spec-15-sequeless-automation.md`, section
10: Q4 and Q5 (the ports), O-5 (the read view's values), O-6 (a denied read), O-8 (this ADR) and Q6 (who
builds the payloads).

## Decision

We will define two ports in `automation-api`, and the host will implement them over store-core.

**Reads: `ObjectReadPort`.** A narrow, read-only port with `find(Scope, ObjectId)`. It returns an empty
result only when the object is absent. The result is an `ObjectSnapshot`: the type IRI, the current state,
and the property values keyed by IRI string, using `kernel-values`' `Value`. A read the Store denies throws
`ObjectReadDeniedException`, which is separate from the empty result. A denied read is a misconfigured
Principal, not a stale object, so the claim fails and the row is retried and then dead-lettered under DR-23.

**Writes: `StoreCommandPort`.** A typed port with three commands: `setProperty`, `createObject` and
`fireTransition`. Each takes an automation-owned record. The host binding joins the caller's claim
transaction and never opens `REQUIRES_NEW`, so a claim and its Store write commit or roll back together. The
Store side rejects a write with `StoreCommandRejectedException`, which fails the claim, and the row is
retried.

**Idempotency.** `setProperty` and `createObject` carry a `CommandProvenance`: the source event id (the
claim row id), the action index, and the source row's ontology version. A retried command with the same key
is a no-op. The created object's id is derived from that key by automation, so a retried create never makes
a second object. `fireTransition` carries no provenance; its idempotency comes from its availability guard.

**Who builds the Store-side payload (Q6).** The host binding builds the candidate object, the audit and the
outbox row (`ObjectUpdated` or `ObjectCreated`) from the command. The command carries only the intent, so
the Store owns the change-set invariants: the version check, the audit, and the outbox payload shape. The
payload shape must match the prototype's executor output, and a golden test checks it.

## Consequences

- The ports are public contracts. Other hosts will implement them, so changes are additive only. A breaking
  change needs a new ADR that supersedes this one, not an edit to it.
- `automation-api` depends on `kernel-values`, so the value type's shape is part of its public contract. A
  build check keeps `automation-api` free of Store modules.
- The host binding does the conversion between automation's records and store-core's types, and it has a
  contract test that includes a rollback case.
- The payload logic moves from automation into the host binding. Until the prototype is cut over (spec 17),
  the prototype's own executor and the new binding can drift, so both must be tested against the same
  golden output.
- A denied read dead-letters every row that touches the denied object. That is accepted: a visible dead-letter
  is recoverable, and a silent skip is not.
- `ObjectReadPort` may later be superseded by the query side's read port (spec 14). That is a follow-up, not
  a coupling now.
