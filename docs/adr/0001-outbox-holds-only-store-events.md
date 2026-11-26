# 0001. The Store's outbox holds only Store events; automation owns Side-effect durability

- Status: accepted
- Date: 2026-10-18 (decided in sequeless/sequeless-prototype#20; committed to this repo on 2026-11-26)

## Context

In `sequeless-prototype`, firing a transition wrote `TransitionFired` together with one `ActionRequest`
row per action and `TimerScheduled` and `TimerCancelled` rows, all in the Store's transaction. Signals
were also queued there. The outbox was therefore the durable queue for automation's work, and the
Command side had to know the shape of every action.

Automation's work is not a Store's fact. Timers and signals are not things a Store did, and each new
action kind would change the Store's table contract.

## Decision

We will narrow the outbox to **Store events** only: an object created, updated or deleted, and a
transition fired, each stamped with the Ontology version it was committed under.

The host relays each Store event. `sequeless-automation` evaluates triggers against the automation
contract of that version and hands the resulting Side effects to its runtime adapter, which owns retries,
idempotency and timers. A Side effect aimed at a Store comes back as a Store Command through store-core's
inbound port, so authorization and validation apply. The reads and writes automation needs go through
its own ports, as recorded in ADR 0002.

Timers are scheduled by automation from a Store event. When one elapses, automation sends a "fire
transition" Store Command, guarded by the expected state. Signals become a Store Command on the Command
API, answered synchronously.

## Consequences

- The Command side and automation can be released independently. The outbox stays a log of what happened,
  which a later move to event sourcing can build on.
- Durability of pending Side effects and timers moves to the runtime. Temporal is the durable production
  runtime. The in-process adapter is in-memory and only for development and tests.
- Store events must carry the Ontology version, and behaviour and automation definitions are looked up by
  (tenant, version), so an in-flight event is handled under the rules it was committed under.
- Recomputing materialised rollups stays the Command side's own consumer of Store events, not automation.
  The host runs it before the automation ingress.
- Trade-off: a row written before this decision, `ActionRequest`, `TimerScheduled`, `TimerCancelled` or
  `SignalReceived`, is not a Store event. Those kinds are drained and retired by the migrations of
  `sequeless-prototype` (spec 13), not by this repo.
