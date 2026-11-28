# 0003. Upstream automation failure port

- Status: proposed
- Date: 2026-11-28

## Context

The Temporal adapter runs each per-action path as a workflow with an activity. Three kinds of failure end that path
without reaching any host-visible record:

1. Activity-level refusals: a missing or mismatched `ontologyVersion` stamp, and an index outside the automation's list.
   They are thrown before the `ActionExecutor` runs.
2. Webhook delivery failures: delivery happens in the activity, outside the executor.
3. A final failure of `resolveWebhookAction`: the released API returns an action or request, so the failure is rethrown
   and the workflow fails.

A host that wrapped the executor and the trigger evaluator could only see failures inside them. The three cases above
were not visible to it, so the host lost its owner-facing failure record for them (spec 17, section 9). The failures
still show in Temporal history and in logs.

Constraints: the adapter must not gain a host dependency. The port must be additive, so existing constructors and bean
wiring keep working. Temporal retries an activity, so a failure must be recorded only once its retries are used up.

## Decision

We will add an outbound port, `AutomationFailureRecorder`, with one method, `record(AutomationFailure)`, in
`org.sequeless.automation.api`. The host implements it, as it implements the other host-owned ports there.
`AutomationFailure` carries the scope, the workflow id, the workflow type, the transition (nullable) and a cause string.

The Temporal adapter records a failure from the workflow, not from inside the activity:

- `TransitionActionWorkflowImpl` catches the `ActivityFailure` that Temporal throws only after the activity has used its
  retries or failed non-retryably. It calls a new activity method, `recordTransitionActionFailure`, and then rethrows
  the original failure, so the workflow still fails.
- The record activity's failure is attached to the original as suppressed, and the original failure still propagates.

The port is additive:

- `ActionActivitiesImpl` gains a three-argument constructor. The two-argument constructor remains and records nothing.
- The Spring autoconfiguration passes the host's recorder if a bean exists, and a no-op recorder otherwise.

The workflow id is `transitionFired.id() + ":" + actionIndex`, the id `TemporalAutomationPort` already uses.

## Consequences

- The recorder is called for every final failure of the per-action path, not only the three cases. The three cases are
  the minimum. Recording every final failure is deliberate, because the workflow cannot tell the three apart without
  matching on exception text. This includes `applyAction` failures, which the host's executor wrapper also records
  today. The workflow id is the dedupe key. A host must key its records by it, so that a second record for the same id
  is a no-op. The two causes of a repeat call are a retry of the recording activity, and the host's own executor
  wrapper. The port's javadoc states this as an idempotency requirement.
- A `StartToClose` timeout on the final attempt is recorded by the same catch. No test covers this path, so spec 17
  open item (a) is not marked closed.
- Recording happens in a separate activity, so the record can itself be retried. A recorder that keeps throwing keeps
  the workflow retrying that record step. It does not hide the original failure.
- The port has no in-process adapter counterpart. The in-process adapter keeps its own `WebhookDeliveryException` path
  and is not changed.
- Tests run through a `TestWorkflowEnvironment`. Each refusal, webhook, resolve and recorder-failure case has a test, and
  each records exactly once, after the last attempt.
