package org.sequeless.automation.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;
import java.util.Map;
import java.util.Set;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;

/**
 * The real {@link TransitionActionWorkflow} implementation. Temporal instantiates this class
 * reflectively, once per execution, so it keeps the implicit public no-arg constructor.
 *
 * <p>Its {@link ActivityOptions} come from {@link TemporalActivityOptions}, the same published value
 * {@link TimerWorkflowImpl} reads, so every activity this workflow calls shares the retry policy the Spring
 * autoconfiguration builds.
 */
public final class TransitionActionWorkflowImpl implements TransitionActionWorkflow {

    /**
     * Applies the action, and on failure records it through {@link ActionActivities#recordTransitionActionFailure}
     * before failing the workflow as before.
     *
     * <p>Temporal throws {@link ActivityFailure} only once the activity has used its retries, or has failed with a
     * non-retryable failure, so the catch below sees the final failure and no earlier attempt. A failure of the record
     * activity itself is attached to the original as suppressed, so the original stays the workflow's failure.
     */
    @Override
    public void execute(StoreEventRow entry, int actionIndex) {
        ActionActivities activities = Workflow.newActivityStub(ActionActivities.class, TemporalActivityOptions.get());
        Scope scope = scopeFrom(entry);
        try {
            activities.applyTransitionAction(scope, entry, actionIndex);
        } catch (ActivityFailure failure) {
            try {
                activities.recordTransitionActionFailure(scope, entry, actionIndex, describe(failure));
            } catch (ActivityFailure recordFailure) {
                failure.addSuppressed(recordFailure);
            }
            throw failure;
        }
    }

    /**
     * Describes the root failure of an activity as {@code type: message}. For a Temporal {@link ApplicationFailure}
     * that is its type and original message. For any other cause it is the class name and message. The type alone is
     * used when there is no message.
     */
    static String describe(ActivityFailure failure) {
        Throwable cause = failure.getCause();
        if (cause instanceof ApplicationFailure application) {
            String message = application.getOriginalMessage();
            return message == null || message.isBlank()
                    ? application.getType()
                    : application.getType() + ": " + message;
        }
        String name = cause == null
                ? ActivityFailure.class.getName()
                : cause.getClass().getName();
        String message = cause == null ? null : cause.getMessage();
        return message == null || message.isBlank() ? name : name + ": " + message;
    }

    /**
     * Reconstructs a {@link Scope} from {@code entry.payload()}'s own {@code tenantId}/{@code
     * principalId} fields. The principal is rebuilt from its id alone, with no roles, as the prototype's
     * test fixtures rebuild it.
     */
    private static Scope scopeFrom(StoreEventRow entry) {
        Map<String, Object> payload = entry.payload();
        String tenantId = (String) payload.get("tenantId");
        String principalId = (String) payload.get("principalId");
        return new Scope(new TenantId(tenantId), new Principal(principalId, Set.of()));
    }
}
