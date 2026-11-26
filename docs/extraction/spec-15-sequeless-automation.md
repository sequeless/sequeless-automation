# Spec 15: Stage 4, `sequeless-automation`

Issue: [#43](https://github.com/sequeless/sequeless-prototype/issues/43). Map: [#11](https://github.com/sequeless/sequeless-prototype/issues/11). Plan: [#23](https://github.com/sequeless/sequeless-prototype/issues/23). Decisions: [#20](https://github.com/sequeless/sequeless-prototype/issues/20) (automation placement), [#19](https://github.com/sequeless/sequeless-prototype/issues/19) (what stays in the prototype), [#41](https://github.com/sequeless/sequeless-prototype/issues/41) (spec 13, closed). Tracker: `docs/extraction/remaining-work.md`.

Status: draft, written 2026-11-26 against `origin/main` `48a0cc6` (2026-11-25). The repo exists (initial commit `acb9805`, the template tree). This document rides in the bootstrap PR (branch `spec-15-bootstrap`), the first code PR of `sequeless/sequeless-automation`, not in a docs-only PR. Decisions marked **Navigator** were settled in the Navigator session for batch 1 (five questions; section 10).

## 1. Identity, stage and dependencies

- **Stage 4, shipping spec.** Spec 15 of 19. It moves the automation code out of the prototype into `sequeless/sequeless-automation`. The prototype keeps its copies until spec 17 (#45) cuts over and deletes them.
- **Waits for** (all `CLOSED` at `48a0cc6`, checked with `gh issue view`): spec 5 [#33](https://github.com/sequeless/sequeless-prototype/issues/33), spec 8 [#36](https://github.com/sequeless/sequeless-prototype/issues/36), spec 11 [#39](https://github.com/sequeless/sequeless-prototype/issues/39), spec 13 [#41](https://github.com/sequeless/sequeless-prototype/issues/41).
- **Does not wait on** spec 14 ([#42](https://github.com/sequeless/sequeless-prototype/issues/42), `sequeless-query`), so the two run in parallel. Q5 (section 10) rules out `sequeless-query`'s `ReadPort` for this reason; see O-7.
- **Blocks** spec 17 ([#45](https://github.com/sequeless/sequeless-prototype/issues/45)), which consumes the released automation artifacts and deletes the prototype's copies.
- **Repo.** `sequeless/sequeless-automation`, public, created from `sequeless/sequeless-template` (the same way `sequeless-expression` and `sequeless-kernel` were). **Status: created** (O-1, resolved 2026-11-26). The agent's own creation call was refused by the auto-mode permission classifier; the owner allowed it, and the parent created the repo from the template, public, with the initial commit `acb9805`.
- **Prototype.** Spec 15 makes no prototype PR. The parent's ruling (2026-11-26) supersedes the earlier O-2 answer: the port changes R2 to R4 live in the new repo, and the prototype keeps its old behaviour until spec 17. The basis is the 2026-11-05 supersession of the pre-factor plan (`docs/extraction/prefactor-plan.md`, line 3: "no pre-factor code PRs will be opened"), which governs specs 14 to 19. Divergence: the prototype and `sequeless-automation` differ in behaviour until spec 17 (section 10, D-1).
- **This spec does not:**
  - edit `sequeless-prototype`'s code (no prototype PR in spec 15; its deletions are spec 17);
  - change `sequeless-query` (spec 14) or `sequeless-ontology`'s `automation-contract` (released 0.1.0);
  - remove `DerivationRecomputer` from the prototype (it stays in store-core, #43);
  - edit `sequeless-build` (the BOM entry) until the parent confirms that cycle 3's BOM entry for `sequeless-query` has merged (section 6).

## 2. Moves

PROVENANCE content for the new repo's `PROVENANCE.md`, in the shape of `sequeless-expression`'s:

- **Created from:** `sequeless/sequeless-template`. The starting commit is the template's `Initial commit`. The template-only files (`template-core/`, `scripts/init-repo.sh`, `docs/specs/stage-0-sequeless-template.md`, `.github/workflows/template-smoke.yml`) are removed by hand in the first commit, as spec 8 did.
- **Source repo:** `sequeless/sequeless-prototype`.
- **Source SHA:** `48a0cc6` (`origin/main` at cycle start, subject `refactor(automation): name the ingress by ingest, not dispatch (Refs #41) (#217)`). It is also the last commit that touches the paths below: `git log -1 --format='%h %ad %s' --date=short origin/main -- <paths>`. The move commit records the SHA again.
- **Date:** the author date of the move commit. Target 2026-11-26 or the day the move lands.
- **Source paths (67 files under these paths at `48a0cc6`, from `git ls-tree -r --name-only`):**
  - `sequeless-spi/src/main/java/org/sequeless/spi/automation/` and `sequeless-spi/src/test/java/org/sequeless/spi/automation/`
  - `sequeless-core/src/main/java/org/sequeless/core/automation/` and `sequeless-core/src/test/java/org/sequeless/core/automation/`
  - `sequeless-adapter-automation-inprocess/` (main, test, resources)
  - `sequeless-adapter-automation-temporal/` (main, test, resources)
  - `sequeless-spi-testkit/src/main/java/org/sequeless/testkit/automation/`
  - `sequeless-app/src/main/java/org/sequeless/app/automation/` (this stays in the host; see below)

The move table. "Stays" means no copy: the file is not in the new repo.

| Source (prototype, `48a0cc6`) | Destination (new repo) | Notes |
|---|---|---|
| `spi/automation/`: `ActionExecutor`, `AutomationPort`, `ObjectChangedEvent`, `ResolvedTimer`, `ResolvedWebhookRequest`, `StoreEvent`, `TransitionFiredEvent`, `TriggerEvaluator`, `package-info` | `automation-api` | Rewritten: `OutboxEntry` becomes the row record (R1). `TransitionFiredEvent` and `ObjectChangedEvent` keep their shapes |
| `spi/automation/TimerScheduledEntry.java` | `automation-api` | Rewritten to build the row record, not `OutboxEntry` (R1) |
| `spi/automation/DerivationRecomputer.java` | **stays** in the prototype, in store-core | #43. The relay calls it before `ingest` (section 3, R7) |
| `spi/automation` tests: `StoreEventTest`, `TimerScheduledEntryTest` | `automation-api` tests | |
| `core/automation/`: `DefaultTriggerEvaluator`, `DefaultActionExecutor`, `package-info` | `automation-core` | Rewritten against `ObjectReadPort` and `StoreCommandPort` (R2, R3). Imports of `ObjectStorePort`, `BusinessObject`, `ChangeSet` and `TransitionService` leave |
| `core/automation` tests: `DefaultTriggerEvaluatorTest`, `DefaultActionExecutorTest` | `automation-core` tests | Store fakes are replaced by the testkit fakes (R8) |
| `sequeless-adapter-automation-inprocess/` main: `InProcessAutomationPort`, `InProcessTimerScheduler`, `InProcessAutomationProperties`, `WebhookDeliveryException`, `package-info`, `InProcessAutomationAutoConfiguration` | `automation-inprocess-adapter` (the autoconfig class: O-3) | The adapter keeps its own `HttpClient` delivery code (Q1, B) |
| `sequeless-adapter-automation-inprocess/` tests and `META-INF/spring/...AutoConfiguration.imports` | `automation-inprocess-adapter` | |
| `sequeless-adapter-automation-temporal/` main: 16 Java files, including `TemporalAutomationPort`, `TemporalAutomationProperties`, `ChangeEventWorkflow(Impl)`, `TimerWorkflow(Impl)`, `TransitionActionWorkflow(Impl)`, `ActionActivities(Impl)`, `TriggerActivities(Impl)`, `TemporalActivityOptions`, `WebhookActivityException`, `TemporalAutomationAutoConfiguration`, `package-info`; and the imports file | `automation-temporal-adapter` (the autoconfig class: O-3) | Workflow and activity type names must not change (hazard H1) |
| `sequeless-adapter-automation-temporal/` tests (9 files) and `src/test/resources/timer-histories/*.json` (2 files) | `automation-temporal-adapter` | The histories are replayed; they must still replay (H2) |
| `sequeless-spi-testkit/src/main/java/org/sequeless/testkit/automation/`: `AutomationContract`, `RecordingActionExecutor`, `RecordingTriggerEvaluator`, `package-info` | `automation-testkit` | Adds the fakes for R2 and R3 (R8) |
| `sequeless-app/src/main/java/org/sequeless/app/automation/`: `OutboxRelay`, `StoreEvents`, `package-info` (app tests stay with them) | **stays** in the host | #20 point 4: the relay stays in the host. Its mapping to the row record is new code in spec 17 (R7) |

Non-moving pieces that the copy depends on and must not carry (R5): `org.sequeless.spi.outbox.OutboxEntry` (stays Store-side; `DerivationRecomputer` still takes it), `org.sequeless.core.ontology.SchemaAndBehaviour` and `org.sequeless.core.statemachine.PayloadValueCodec` (both used by `DefaultTriggerEvaluator`, lines 87 and 13).

Not moved: the contract types. `org.sequeless.ontology.automation.api` (`Action`, `WebhookAction`, `SetPropertyAction`, `CreateObjectAction`, `LogAction`, `TransitionRef`, `Automation`) and `org.sequeless.ontology.automation.spi.AutomationSource` stay in `sequeless-ontology`, released as `automation-contract` 0.1.0 (tracker, "Release 0.1.0 is out"). The new repo depends on them; it never copies or re-exports them (section 4).

## 3. Prerequisite refactors

Everything below happens in the new repo, on the copy, before the copy is used. **There is no prototype prerequisite PR** (the parent's ruling, 2026-11-26). R2 to R4 change the ports in the new repo, and the prototype keeps its old behaviour until spec 17. Basis: the 2026-11-05 supersession of `docs/extraction/prefactor-plan.md` (line 3), which covers specs 14 to 19 and overrides `#23` point 2 for them. Spec 14 is on the same footing.

**What the commit path does today (checked at `48a0cc6`).** This matters for R3, because the Store side's work moves behind the port:

- `DefaultActionExecutor.commitSetProperty` (lines 135 to 175) reads the object with `objectStorePort.find`, compares the value, builds the candidate `BusinessObject` (version + 1, the `Audit` with the original `createdAt`/`createdBy`), builds the `ObjectUpdated` outbox row with `outboxPayload` (lines 300 to 310: `objectId`, `version`, `typeIri`, and `ontologyVersion` copied from the source row), and calls `objectStorePort.commit(scope, new ChangeSet(List.of(new Update(candidate, current.version())), List.of(outboxEntry)))` (line 174).
- `DefaultActionExecutor.commitCreateObject` (lines 183 to 216) derives the new id with `derivedCreateId(rowId, actionIndex)` (lines 280 to 283: a name-based UUID of `rowId:actionIndex`), checks `find` for idempotency, builds the candidate with version 1, builds the `ObjectCreated` row, and calls `commit` (line 215).
- So the Store-event payloads for automation-issued writes are built on the automation side today. Once the write is a Store Command, the Store side (the host binding over store-core) must build the candidate, the audit, and the outbox row, or the command must carry them. **Open: Q6 (section 12).**

- **R1. The row record replaces `OutboxEntry` in the automation API.** `automation-api` defines `StoreEventRow(UUID id, String kind, Map<String, Object> payload, Instant occurredAt)`, a record with the same four fields as `org.sequeless.spi.outbox.OutboxEntry` (`record OutboxEntry(UUID id, String kind, Map<String, Object> payload, Instant occurredAt)`, `sequeless-spi/.../outbox/OutboxEntry.java` line 42). **Navigator (Q3, option A).** `ActionExecutor` (import at line 6 today), `TriggerEvaluator` (line 5), and `TimerScheduledEntry.of` take and return this record. The host relay maps `OutboxEntry` to it. The kind stays a string, not an enum. The encoding is additive only once published.
  - The static helper `OutboxEntry.timerKey(objectId, stateIri, transitionName)` is used by `ResolvedTimer`'s javadoc and by `DefaultTriggerEvaluator` (line 203). Its formula moves into `automation-api` unchanged, and a test pins its output. Timer keys name the Temporal timer workflows, so a changed key would orphan a running timer (H3).
- **R2. `ObjectReadPort` replaces `ObjectStorePort.find`.** **Navigator (Q5, option A).** `automation-api` gets a narrow, read-only `ObjectReadPort` with `find(Scope, ObjectId)`, returning an `Optional` of a view holding the type IRI, the current state and the property values. The evaluator reads through it at `DefaultTriggerEvaluator` lines 101, 148 and 219. The host implements it over store-core's `find`, inside the same claim transaction, so it sees the claimant's own uncommitted writes (no `REQUIRES_NEW`, no cache, no replica).
  - The view's value types: **Navigator's wording is "automation-owned value types".** The evaluator also uses `Value` and `ObjectId` from `kernel-values`, which the API may depend on (as `expression-api` does). This spec reads the Navigator's wording as "no Store types". Confirm this in the next Navigator batch (O-5).
  - Authorization on the read: a read denied for the automation Principal either counts as missing (a no-op) or as an error. The spec must choose (O-6).
- **R3. `StoreCommandPort` replaces the direct `ObjectStorePort.commit`.** **Navigator (Q4, option A), amended.** `automation-api` owns a typed Store Command port with automation-owned records. It starts with `SetProperty` and `CreateObject`, the two commits at `DefaultActionExecutor.java` lines 174 and 215, and it also carries the guarded fire-transition command. The evaluator calls `transitionService.fireAutomated` at `DefaultTriggerEvaluator` lines 159 and 240, which is a Store Command, not a `SetProperty` or `CreateObject` (#20 point 6, timers belong to automation). The host binds the port in-process to store-core's inbound Command port, joining the caller's claim transaction (no `REQUIRES_NEW`), running as the automation Principal, so authorization and validation apply.
  - **Idempotency for at-most-once (Navigator risk 2).** The key is the claim row id plus the action index. A retried command with the same key is a no-op. A rollback releases the claim, and the action runs again without double-applying.
  - **Rejection.** A rejected command maps to a claim outcome (failed or retried). It never leaves a half-applied action.
  - **The port has no `ObjectStorePort`, `ChangeSet`, `BusinessObject` or `PayloadValueCodec` types.** The host binding converts.
- **R4. `TransitionService` leaves automation.** The two `fireAutomated` call sites become R3's fire-transition command. `DefaultTriggerEvaluator` no longer imports `org.sequeless.core.api`.
- **R5. Non-moving helpers.** `SchemaAndBehaviour.read(tenant, schemaSource, behaviourSource)` (line 87) and `PayloadValueCodec` (`decodePropertiesSnapshot`, line 99) are needed by the evaluator. Either the evaluator copies the subset it uses into `automation-core`, or it consumes the ontology contracts directly. **UNVERIFIED:** which methods of `SchemaAndBehaviour` and `PayloadValueCodec` the evaluator uses. Check when the copy is made.
- **R6. Javadoc.** Links to prototype types that do not move are demoted to `{@code}` or replaced, as spec 5 section 3.1 did. Affected: `ActionExecutor`, `TriggerEvaluator`, `DerivationRecomputer` (not moved), `AutomationPort` (which names `org.sequeless.core.*` in its javadoc), and the `package-info` files. The `package-info` of `org.sequeless.spi.automation` describes the three inverted ports; rewrite it for the new layout.
- **R7. The relay (host) runs the recompute.** The relay calls `DerivationRecomputer.recompute(Scope, OutboxEntry)` for object-lifecycle rows, before `AutomationPort.ingest`. This is not a copy: the relay and the recomputer stay in the prototype until spec 17. The new repo states the call order in its `AutomationPort` javadoc only.
- **R8. Test fakes.** `DefaultTriggerEvaluatorTest` and `DefaultActionExecutorTest` use `ObjectStorePort` fakes today. They move to fakes of `ObjectReadPort` and `StoreCommandPort`. `automation-testkit` ships in-memory versions of both, which must model the transaction semantics of R3 (rollback releases the claim).

## 4. Package renames

Decided in the Navigator session (Q2, confirmed). The repo-segment rule (kernel ADR 0001, status `proposed`) and the expression precedent (`org.sequeless.expression.{api,jexl,testkit,autoconfigure}`, spec 9).

| Old (prototype) | New (`sequeless-automation`) | Module |
|---|---|---|
| `org.sequeless.spi.automation` | `org.sequeless.automation.api` | `automation-api` |
| `org.sequeless.core.automation` | `org.sequeless.automation.core` | `automation-core` |
| `org.sequeless.adapter.automation.temporal` | `org.sequeless.automation.temporal` | `automation-temporal-adapter` |
| `org.sequeless.adapter.automation.inprocess` | `org.sequeless.automation.inprocess` | `automation-inprocess-adapter` |
| `org.sequeless.testkit.automation` | `org.sequeless.automation.testkit` | `automation-testkit` |
| autoconfig classes (in the adapter modules today) | `org.sequeless.automation.autoconfigure` | `automation-spring-boot-autoconfig` |

Rules:

- The adapter packages use the bare technology name (`.temporal`, `.inprocess`), not `.adapter.temporal`, matching `org.sequeless.expression.jexl`.
- **Collision:** the ontology contract is `org.sequeless.ontology.automation.{api,spi}`. Its types are never copied into `org.sequeless.automation.api` and never re-exported from it. Simple names repeat (`Action`, `WebhookAction`), so imports must be checked by the boundary test (section 7).
- **Two copies on one classpath.** The prototype keeps `org.sequeless.spi.automation` and `org.sequeless.core.automation` until spec 17. The new names differ, so the packages cannot clash on a classpath. But two autoconfigs would register two `AutomationPort` beans if both jars were loaded into one application. The prototype must not depend on the new artifacts before spec 17, and the BOM entry lands only after the parent's signal (section 6).
- **Provisional names.** The names depend on kernel ADR 0001 being accepted. It is `proposed`, so these names are provisional until it is.
- **No bridge package.** Renames happen in place in the new repo, as in spec 8 section 4. Consumers change their imports in spec 17. Spec 17 lists the import migration as a step.
- **Temporal names.** The Temporal workflow and activity type names come from the Java interfaces. Check that they do not change when the packages move (H1).

## 5. Modules and their dependencies

Six modules. **Navigator (Q1, option B):** the webhook HTTP adapter is deferred. The module list is shorter than #20's list (see section 10, deferred items).

| Module | Packages | Depends on (main) | Test scope |
|---|---|---|---|
| `automation-api` | `org.sequeless.automation.api` | `kernel-identity` (`Scope`, `TenantId`, `Principal`), `kernel-values` (`ObjectId`, `Value`), `automation-contract` 0.1.0 (`Action`, `WebhookAction`, `TransitionRef`). No Store types, no expression, no Spring | JUnit, AssertJ |
| `automation-core` | `org.sequeless.automation.core` | `automation-api`; `expression-api` 0.1.1 (`ExpressionPort`, used by `DefaultActionExecutor` to render templates); `kernel-values`; `schema-contract`, `behaviour-contract`, `automation-contract` 0.1.0 (for `SchemaSource`, `BehaviourSource`, `AutomationSource`) | `automation-testkit`, JUnit, AssertJ |
| `automation-inprocess-adapter` | `org.sequeless.automation.inprocess` | `automation-api`; `automation-contract` 0.1.0; JDK `java.net.http` (no new dependency). Spring context, optional | `automation-testkit`, `automation-core` |
| `automation-temporal-adapter` | `org.sequeless.automation.temporal` | `automation-api`; Temporal SDK (the version the prototype pins); Spring context, optional | `automation-testkit`, Temporal test support |
| `automation-testkit` | `org.sequeless.automation.testkit` | `automation-api`; `kernel-identity`, `kernel-values`; JUnit, AssertJ | JUnit, AssertJ |
| `automation-spring-boot-autoconfig` | `org.sequeless.automation.autoconfigure` | `automation-core`; the two adapters, optional; `spring-boot-autoconfigure` | `automation-testkit` |

- **Adapters depend only on `automation-api`**, never on `automation-core` in main code. This is the rule the prototype has as `noAdapterDependsOnCore`. It is restated as a boundary test (section 7).
- **Autoconfig placement (O-3, open).** Today each adapter owns its autoconfig class (`InProcessAutomationAutoConfiguration`, `TemporalAutomationAutoConfiguration`), selected by `sequeless.automation.adapter`. The plan follows the expression precedent: the autoconfig classes move into `automation-spring-boot-autoconfig`, which holds the one property switch. This is not yet a Navigator decision.
- **Deferred, in #20, not in spec 15:**
  - `automation-webhook-http-adapter` (Q1, option B). The webhook HTTP call stays in both adapters, which each hold their own delivery code (`InProcessAutomationPort` lines 92 to 265; `ActionActivitiesImpl` lines 43 to 139). Risks: the delivery code is duplicated, a fix is made twice, and a webhook port added later must be additive. `ResolvedWebhookRequest` stays unchanged and carries no HTTP client types.
  - `automation-postgres-adapter` (subscriptions, #20). There are no subscriptions in the prototype, so nothing moves.
  - Store Command HTTP adapter and broker adapter (#20, deferred there).
- **Versions.** `automation-contract`, `schema-contract` and `behaviour-contract` are released at 0.1.0 (module tags published with the ontology 0.1.0 release). `expression-api` is at 0.1.1. The versions the first PR uses are the released ones, confirmed on GitHub Packages at that time (no SNAPSHOT, the spec 7 rule).

## 6. Release config (first version, tags, BOM change)

- **First version.** 0.1.0 for each of the six modules. Not a major release, so the parent's standing pre-approval applies.
- **Mechanism.** As spec 8 section 6: release-please in manifest mode with the `maven-workspace` plugin, `release-as: 0.1.0` for the first release, and a follow-up PR that removes the pin. The repo's `.release-please-manifest.json` and `release-please-config.json` start empty (the template's values, which name `template-core`, are rewritten).
- **Tags.** `<module>-v0.1.0`, with `include-component-in-tag` true, as the kernel does.
- **Deploy namespace.** Each module POM sets `sequeless.github.repository` to `sequeless-automation`. Without it the parent deploys to `sequeless-build` and gets a 403 (spec 5 section 6).
- **BOM change (BLOCKED, O-4).** `sequeless-bom` in `sequeless/sequeless-build` gains the six automation artifacts at 0.1.0 after their release. The `bom-contracts` module gets an `AutomationBomContractTest` that runs `automation-testkit`'s contract. The BOM's CI does not run any testkit contract today (spec 8 section 6), so this is the first. **This edit waits for the parent to confirm that cycle 3's BOM entry for `sequeless-query` has merged** (the prompt's step 6). Until then, nothing in `sequeless-build` changes.
- **Release PR.** Pre-approved (minor or patch, and the first 0.1.0 is not a major release). Merged after checks pass, with no `--admin` bypass of a red check.

## 7. CI and workflows

- **New repo.** `ci.yml`, `codeql.yml`, `release.yml` and `pr-title.yml` come from the template and call the reusable workflows in `sequeless/sequeless-build` at the latest workflows tag at PR time. Spec 8 recorded `workflows-v0.1.2`, which must be re-checked. The template's `template-smoke.yml` is removed.
- **Java level.** Release 25. The prototype builds on JDK 25 (spec 13 gate), and `sequeless-build`'s `bom-contracts` compiles at release 25 (tracker). **UNVERIFIED:** the template's setting, to be checked in the first PR.
- **Gate, per PR.** `source ~/.bashrc.ext && export JAVA_HOME=/opt/sdkman/candidates/java/25.0.3-tem && mvn spotless:apply && mvn -B -Prelease verify`, with `~/.bashrc.ext` sourced in the same command as every `mvn` run (it carries the Packages token). Maven runs one at a time across children: `flock -w 1800 $HOME/.mvn-build.lock mvn ...`, with `MAVEN_OPTS=-Xmx1g` and `-T 1`. Run one IT class at a time. If a JVM cannot reach a Testcontainers port, set `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`.
- **Boundary test per module (ADR 0001 needs one).** An ArchUnit test per module that enforces:
  - `automation-api` imports only `java.*`, `org.sequeless.kernel.*` and `org.sequeless.ontology.automation.*`;
  - `automation-core` does not import `org.sequeless.spi.*`, `org.sequeless.core.*` or any Store type;
  - adapters depend only on `automation-api` in main code;
  - no type under `org.sequeless.automation.*` shares a simple name with a type in `org.sequeless.ontology.automation.*` (the collision rule, section 4).
- **Contract tests.** `automation-testkit`'s `AutomationContract` runs against both adapters and against `automation-core`. R3 and R2 each get a contract clause: rollback releases the claim, and a missing object is a no-op.
- **Prototype CI.** Unchanged in this spec. Its pin to `sequeless-build` does not move.

## 8. ADRs

- **Automation 0001, "The Store's outbox holds only Store events; automation owns Side-effect durability".** The draft is on the prototype branch `wayfinder/automation-outbox-adr` at `docs/drafts/sequeless-automation-adr-0001-outbox-holds-only-store-events.md` (dated 2026-10-18, status `proposed`, decided in #20). It is committed as `docs/adr/0001-outbox-holds-only-store-events.md` in the new repo, using the template's `docs/adr/_template.md`. Status on commit: **accepted**, because #20 decided it. The decision date stays 2026-10-18.
- **Not new ADRs.** The Navigator decisions (Q1 to Q5) are recorded in this spec. Spec 13 kept port-level decisions in its spec, not in ADRs. Whether R2 and R3 warrant an ADR 0002 is open (O-8).
- **Kernel ADR 0001** (`proposed`) governs the package names (section 4). The automation ADR does not depend on it.

## 9. Definition of done

Copied from #43, with the status at `48a0cc6`:

- "The new repo's CI is green." **Open.** No repo yet.
- "The prototype's CI is green against released artifacts (never a SNAPSHOT on main)." **Not applicable in spec 15.** The prototype is unchanged in this cycle. It applies at spec 17.
- "The BOM's CI, which runs every testkit contract, is green." **Open.** Blocked on O-4.
- "The release and the BOM re-release are done." **Open.** Release follows the repo; the BOM re-release follows O-4.
- "For cut-over specs: the prototype deleted the moved code in a commit that names the target repo and the import commit's SHA." **Not applicable in spec 15.** The deletion is spec 17's job, and the commit will name this repo and the import commit's SHA.

## 10. Navigator decisions and open items

Navigator session `e8ce4259e32b4640adc5f3be70d39d9d`, batch 1 (five questions, ended 2026-11-26).

| Q | Question | Decision | Reason |
|---|---|---|---|
| Q1 | Webhook HTTP adapter now, or deferred? | **B, defer** (six modules) | A move; a new adapter needs a port the prototype lacks. Both adapters hold duplicated delivery code (verified) |
| Q2 | Package names | **Confirmed**: `org.sequeless.automation.{api,core,temporal,inprocess,testkit,autoconfigure}`; adapter packages bare, not `.adapter.*` | Repo-segment rule and expression precedent (verified in `expression`'s `PROVENANCE.md`) |
| Q3 | Outbox row type | **A**: `automation-api` owns `StoreEventRow`; the host maps `OutboxEntry` to it. `StoreEvent`, `ObjectChangedEvent` and `TransitionFiredEvent` go in `org.sequeless.automation.api` | `sequeless-automation` never depends on store-core, so the row cannot be a Store type. A shared module would be a fourth repo for one producer |
| Q4 | Store Command port | **A**: typed port in `automation-api`, host binds in-process inside the claim transaction. **Amended:** the port also carries the guarded fire-transition command | Store actions must not depend on store-core (#20 point 5). `DefaultTriggerEvaluator` fires through `TransitionService` at lines 159 and 240 |
| Q5 | How automation reads object state | **A**: narrow read-only `ObjectReadPort` in `automation-api`, host-implemented, same claim transaction | Evaluator needs current state, type IRI and property values, including reference ids (lines 101, 148, 219). B would make spec 15 wait on spec 14. C cannot work: timer and candidate reads need fresh state |

**Open items:**

- **O-1 (resolved 2026-11-26).** The agent's `gh repo create --template` call was refused by the auto-mode permission classifier. The owner allowed it, and the parent created `sequeless/sequeless-automation`. The agent did not retry the call.
- **O-2 (resolved by the parent, 2026-11-26).** No prototype prerequisite PR. R2 to R4 live in the new repo. Basis: the 2026-11-05 pre-factor supersession (prefactor-plan line 3). Recorded in section 3.
- **D-1 (divergence risk, carried to the handoff).** The prototype and `sequeless-automation` differ in behaviour until spec 17. R2 to R4 change the ports, and their behaviour is checked only in the new repo until the cut-over.
- **O-3 (decided, Navigator batch 2, option A).** Both adapters' autoconfig classes move into `automation-spring-boot-autoconfig`, with the one `sequeless.automation.adapter` switch. The adapters and the Temporal SDK are optional dependencies, and nested configurations use `@ConditionalOnClass`. Risks: the adapters' `package-info` must point at the new module; the optional-classpath compile is unverified; the property name and imports file are public contract; spec 17 removes the prototype's imports entries.
- **O-4 (parent).** The BOM entry in `sequeless-build` waits for cycle 3's `sequeless-query` BOM entry to merge (prompt step 6). Nothing in `sequeless-build` changes until the parent says so.
- **O-5 (decided, Navigator batch 2, option A).** The view's property values are `kernel-values`' `Value`, identity is `ObjectId`, and keys are IRI strings, so no Store type leaks. Risk: `automation-api` depends on `kernel-values`, so `Value`'s shape is public and changes are additive only. Add a build check that `automation-api` has no Store-module dependency.
- **O-6 (decided, Navigator batch 2, option B).** A denied `ObjectReadPort` read throws `ObjectReadDeniedException` (in `automation-api`), separate from the empty result. The claim fails, the row is retried, and it is dead-lettered after three failures (DR-23). Risks: a misconfigured Principal dead-letters every row that touches the denied object, so the log and metric name the Principal and the object; the exception must not reveal whether an object exists; a test covers a Principal without read permission ending in dead-letter; per-object denial dead-letters too, accepted behaviour.
- **O-7 (follow-up, not blocking).** `sequeless-query`'s `ReadPort` (spec 14) may later supersede `ObjectReadPort`. Not coupled now.
- **O-8 (open).** Whether R2 and R3 need an ADR 0002.
- **Escalated to the parent and not yet answered:** O-1, O-2, O-4.

## 11. Hazards

- **H1. Temporal names.** Workflow and activity type names come from the Java interfaces. If they change with the package move, running workflows of the old name are orphaned. Check the names in the first PR.
- **H2. Replay.** `TimerWorkflowReplayTest` replays `timer-histories/*.json`. The moved workflow must still replay those histories. The input type of `ChangeEventWorkflow` changes again (`ObjectChangedEvent` to the row record), so the spec 13 rule applies at spec 17: stop the old workers before the new ones start (spec 13 section 3, the Mixed versions order).
- **H3. Timer keys.** `timerKey` is `objectId|stateIri|transitionName`. Changing its formula orphans timers.
- **H4. Two copies.** Do not put the prototype's `spi.automation` and the new `automation.api` on one classpath before spec 17.
- **H5. Persisted payloads.** The outbox payloads are JSON with no package names (UNVERIFIED: check the `OutboxEntryDataConverter` path in the Temporal adapter). The row record keeps the same keys.
- **H6. Idempotency.** R3's key (claim id plus action index) must match the prototype's Temporal key for in-flight actions, or the first run after the move re-applies them.
- **H7. Authorization.** Store-side validation now runs on automation's writes, so some actions the prototype accepted may be rejected. Spec 17 must test the rejections.

## 12. Next actions (for the next run, in order)

1. **Done (O-1).** The repo exists. The bootstrap PR (U2, branch `spec-15-bootstrap`) carries this document.
2. **Done in the bootstrap PR:** this document is at `docs/extraction/spec-15-sequeless-automation.md`.
3. Claim the units with the parent before any code: U3 (code, R1 to R8), U4 (ADR 0001 and release 0.1.0). U5 (the BOM entry) waits for the parent's signal on cycle 3's BOM entry (O-4). Do not touch `sequeless-build` before that.
4. Navigator batch 2: O-3, O-5 and O-6 are decided (section 10). O-8 (ADR 0002) and Q6 (who builds the Store-event payloads for automation-issued writes, section 3) are pending at this revision.
5. Implement R1 to R8 in the new repo, with Haiku subagents, and review each with an Opus subagent before the PR. Each PR needs green CI.
6. ADR 0001 and the release (0.1.0, pre-approved).
7. BOM entry (O-4), after the parent's signal.
