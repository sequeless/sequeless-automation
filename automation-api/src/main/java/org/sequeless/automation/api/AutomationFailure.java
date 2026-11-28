package org.sequeless.automation.api;

import java.util.Objects;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.automation.api.TransitionRef;

/**
 * One automation failure a host may record, as handed to {@link AutomationFailureRecorder#record}. It describes a
 * per-action workflow that has given up: the workflow's retries are used up, or its first failure was not retryable.
 *
 * @param scope the tenant and principal the failed workflow ran for; must not be {@code null}
 * @param workflowId the Temporal workflow id of the failed workflow, for example {@code eventId + ":" + index} for a
 *     transition action; must not be blank
 * @param workflowType the simple name of the failed workflow's interface, for example {@code
 *     "TransitionActionWorkflow"}; must not be blank
 * @param transition the transition the failed action belongs to, or {@code null} if the {@code TransitionFired} row
 *     lacks one of its three keys
 * @param cause a description of the failure: the class or type name and the message of the root failure; must not be
 *     {@code null}, and may be empty
 */
public record AutomationFailure(
        Scope scope, String workflowId, String workflowType, TransitionRef transition, String cause) {

    public AutomationFailure {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(cause, "cause must not be null");
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("AutomationFailure workflowId must not be blank");
        }
        if (workflowType == null || workflowType.isBlank()) {
            throw new IllegalArgumentException("AutomationFailure workflowType must not be blank");
        }
    }
}
