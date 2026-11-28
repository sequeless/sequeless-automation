package org.sequeless.automation.api;

/**
 * The outbound port a host implements to record automation failures that the released adapter would otherwise lose.
 *
 * <p>The Temporal adapter calls it once, when a per-action workflow has given up on a {@code TransitionFired} action:
 * an activity-level refusal (a missing or mismatched ontology-version stamp, or an index outside the automation's list),
 * a webhook delivery failure, a failed webhook resolve, or any other failure its activity returns with. The failure
 * stays visible in Temporal history and logs whether or not a recorder is wired. The recorder adds the owner-facing
 * record.
 *
 * <p><b>Idempotency.</b> The {@link AutomationFailure#workflowId()} is the dedupe key. A recorder may be called more
 * than once for the same key: the Temporal activity that makes the call can itself be retried, and a host may also
 * record the same failure in its own executor wrapper. An implementation should treat a second record for the same
 * workflow id as a no-op.
 *
 * <p>When no recorder bean is present, the Temporal adapter uses a recorder that does nothing.
 */
public interface AutomationFailureRecorder {

    /**
     * Records one failure. Called from inside a Temporal activity, so it may block and may throw; a thrown exception
     * fails that activity and Temporal retries it.
     *
     * @param failure the failure to record; must not be {@code null}
     */
    void record(AutomationFailure failure);

    /**
     * @return a recorder that records nothing, for an adapter with no host recorder wired
     */
    static AutomationFailureRecorder none() {
        return failure -> {};
    }
}
