package org.sequeless.automation.api;

import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.WebhookAction;

/**
 * The inbound port an automation adapter calls to apply one action of a fired transition, through the
 * same validated, audited write path the rest of the Store uses. The automation core implements this
 * port. The adapter builds the action from the automation of the event's ontology version and passes
 * it in, by its 0-based index in that automation's action list.
 */
public interface ActionExecutor {

    /**
     * Applies one action of the fired transition. The writes it makes are sent to the Store through
     * {@link StoreCommandPort}.
     *
     * @param scope the tenant and principal to apply the action on behalf of; must not be {@code null}
     * @param transitionFired the transition-fired row the action originates from; must not be {@code null}
     * @param action the action to apply; must not be {@code null}
     * @param actionIndex the action's 0-based position in the automation's action list
     * @throws IllegalArgumentException if {@code action} is a {@link WebhookAction}; resolve it with
     *     {@link #resolveWebhookAction} instead
     */
    void applyAction(Scope scope, StoreEventRow transitionFired, Action action, int actionIndex);

    /**
     * Renders one webhook action of the fired transition into a {@link ResolvedWebhookRequest}. This
     * method performs no HTTP I/O: the calling adapter delivers the returned request.
     *
     * @param scope the tenant and principal to render the templates on behalf of; must not be {@code null}
     * @param transitionFired the transition-fired row the action originates from; must not be {@code null}
     * @param action the webhook action to resolve; must not be {@code null}
     * @param actionIndex the action's 0-based position in the automation's action list
     * @return the fully-rendered request, ready for the caller to send over HTTP
     */
    ResolvedWebhookRequest resolveWebhookAction(
            Scope scope, StoreEventRow transitionFired, WebhookAction action, int actionIndex);
}
