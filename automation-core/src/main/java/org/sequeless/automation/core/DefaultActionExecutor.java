package org.sequeless.automation.core;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.sequeless.automation.api.ActionExecutor;
import org.sequeless.automation.api.CommandProvenance;
import org.sequeless.automation.api.CreateObjectCommand;
import org.sequeless.automation.api.ObjectReadPort;
import org.sequeless.automation.api.ObjectSnapshot;
import org.sequeless.automation.api.ResolvedWebhookRequest;
import org.sequeless.automation.api.SetPropertyCommand;
import org.sequeless.automation.api.StoreCommandPort;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.expression.api.ExpressionContext;
import org.sequeless.expression.api.ExpressionPort;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.CreateObjectAction;
import org.sequeless.ontology.automation.api.LogAction;
import org.sequeless.ontology.automation.api.PropertyAssignment;
import org.sequeless.ontology.automation.api.SetPropertyAction;
import org.sequeless.ontology.automation.api.WebhookAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reference implementation of {@link ActionExecutor}. An automation adapter calls it once a fired transition's
 * {@code TransitionFired} row has been dispatched, and each action of that transition is applied through it.
 * The row carries the {@code self} map and the transition's keys, and the per-action methods read them from it.
 *
 * <p>Every write this class makes is a command on {@link StoreCommandPort}. Every {@code value}, {@code expression}
 * and template field of an action is resolved through {@link ExpressionPort}, never evaluated here. The host
 * builds the Store's candidate objects, audit records and outbox rows, so this class builds none of them.
 *
 * <p>{@link #applyAction} is designed to be safely retried. A {@code SetProperty} action sends a command only when
 * the target value differs from the property's current value, and is a silent no-op if the target object no longer
 * exists. A {@code CreateObject} action derives the new object's id from the row's id and the action's index, and
 * is a no-op if an object already exists at that id. {@link #resolveWebhookAction} and a {@code Log} action make no
 * command at all: they only render templated text, leaving any I/O to the caller.
 */
public final class DefaultActionExecutor implements ActionExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultActionExecutor.class);

    private final ExpressionPort expressionPort;
    private final ObjectReadPort readPort;
    private final StoreCommandPort commandPort;

    /**
     * @param expressionPort the port every {@code value}, {@code expression} and template field is resolved through;
     *     must not be {@code null}
     * @param readPort the port the current state of an object is read through; must not be {@code null}
     * @param commandPort the port every write is sent through; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultActionExecutor(ExpressionPort expressionPort, ObjectReadPort readPort, StoreCommandPort commandPort) {
        this.expressionPort = Objects.requireNonNull(expressionPort, "expressionPort must not be null");
        this.readPort = Objects.requireNonNull(readPort, "readPort must not be null");
        this.commandPort = Objects.requireNonNull(commandPort, "commandPort must not be null");
    }

    @Override
    public void applyAction(Scope scope, StoreEventRow transitionFired, Action action, int actionIndex) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(transitionFired, "transitionFired must not be null");
        Objects.requireNonNull(action, "action must not be null");

        Map<String, Object> row = transitionFired.payload();
        switch (action) {
            case SetPropertyAction setProperty -> {
                ObjectId objectId = ObjectId.parse((String) row.get("objectId"));
                commitSetProperty(
                        scope,
                        objectId,
                        setProperty.propertyIri(),
                        transitionFired,
                        actionIndex,
                        () -> resolveTarget(
                                setProperty.value().orElse(null),
                                setProperty.expression().orElse(null),
                                buildContext(row)));
            }
            case CreateObjectAction createObject ->
                commitCreateObject(
                        scope,
                        transitionFired,
                        actionIndex,
                        createObject.typeIri(),
                        () -> resolveAssignments(createObject.properties(), buildContext(row)));
            case LogAction log -> logMessage(buildContext(row), log.message());
            case WebhookAction webhook ->
                throw new IllegalArgumentException(
                        "A webhook action is resolved through resolveWebhookAction, not applyAction");
        }
    }

    @Override
    public ResolvedWebhookRequest resolveWebhookAction(
            Scope scope, StoreEventRow transitionFired, WebhookAction action, int actionIndex) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(transitionFired, "transitionFired must not be null");
        Objects.requireNonNull(action, "action must not be null");

        return renderWebhook(buildContext(transitionFired.payload()), action.url(), action.method(), action.body());
    }

    /**
     * The one SetProperty command {@link #applyAction} sends. Does nothing if the target object no longer exists.
     * Otherwise resolves {@code target} (only now, once the object is known to exist) and sends a command only if
     * the value differs from the property's current value.
     */
    private void commitSetProperty(
            Scope scope,
            ObjectId objectId,
            String propertyIri,
            StoreEventRow sourceRow,
            int actionIndex,
            Supplier<Value> target) {
        Optional<ObjectSnapshot> found = readPort.find(scope, objectId);
        if (found.isEmpty()) {
            // The object may have been deleted since the transition fired; this is a safe,
            // retryable no-op, never a failure.
            return;
        }

        Value value = target.get();
        if (Objects.equals(found.get().properties().get(propertyIri), value)) {
            return;
        }

        commandPort.setProperty(
                scope, new SetPropertyCommand(objectId, propertyIri, value, provenance(sourceRow, actionIndex)));
    }

    /**
     * The one CreateObject command {@link #applyAction} sends. Derives the new object's id from {@code sourceRow}'s id
     * and {@code actionIndex}, and does nothing if an object already exists at that id. Otherwise resolves
     * {@code properties} (only now, once that check has passed) and sends the command.
     */
    private void commitCreateObject(
            Scope scope,
            StoreEventRow sourceRow,
            int actionIndex,
            String createType,
            Supplier<Map<String, Value>> properties) {
        ObjectId newId = derivedCreateId(sourceRow.id(), actionIndex);

        if (readPort.find(scope, newId).isPresent()) {
            // Idempotent no-op: a retried activity must never create a second object.
            return;
        }

        Map<String, Value> resolved = properties.get();
        commandPort.createObject(
                scope, new CreateObjectCommand(newId, createType, resolved, provenance(sourceRow, actionIndex)));
    }

    /** Resolves a {@code CreateObject} action's property assignments against {@code context}. */
    private Map<String, Value> resolveAssignments(List<PropertyAssignment> assignments, ExpressionContext context) {
        Map<String, Value> properties = new HashMap<>();
        for (PropertyAssignment assignment : assignments) {
            Value value = resolveTarget(
                    assignment.value().orElse(null), assignment.expression().orElse(null), context);
            properties.put(assignment.propertyIri(), value);
        }
        return properties;
    }

    /**
     * Renders a webhook's url, and its body if one is present, against {@code context}. {@code method} is used
     * verbatim. Used by {@link #resolveWebhookAction}.
     */
    private ResolvedWebhookRequest renderWebhook(
            ExpressionContext context, String urlTemplate, String method, Optional<String> bodyTemplate) {
        String url = expressionPort.renderTemplate(urlTemplate, context);
        Optional<String> body = bodyTemplate.map(template -> expressionPort.renderTemplate(template, context));
        return new ResolvedWebhookRequest(url, method, body);
    }

    /** Renders a {@code Log} action's message against {@code context} and logs it. Used by {@link #applyAction}. */
    private void logMessage(ExpressionContext context, String messageTemplate) {
        String message = expressionPort.renderTemplate(messageTemplate, context);
        LOG.info(message);
    }

    /**
     * Resolves the {@code value}/{@code expression} pair a {@code SetProperty} action or a single {@code CreateObject}
     * property assignment carries: a literal {@code value} if present, else an {@code expression} evaluated through
     * {@link ExpressionPort#evaluate} against {@code context}.
     */
    private Value resolveTarget(Value value, String expression, ExpressionContext context) {
        return value != null ? value : expressionPort.evaluate(expression, context);
    }

    /**
     * Builds the {@link ExpressionContext} every {@code value}, {@code expression} and template field is resolved
     * against: {@code self} decodes directly into {@link ExpressionContext#selfProperties()}, since both are keyed by
     * property short name. The state is the row's {@code toState}: the state the transition moved the object to.
     */
    private ExpressionContext buildContext(Map<String, Object> row) {
        @SuppressWarnings("unchecked")
        Map<String, Object> self = (Map<String, Object>) row.get("self");
        Map<String, Value> selfProperties = new HashMap<>();
        self.forEach((name, taggedValue) -> selfProperties.put(name, PayloadValueCodec.fromPayload(taggedValue)));
        Optional<String> selfState = Optional.of((String) row.get("toState"));
        String principalId = (String) row.get("principalId");
        return new ExpressionContext(selfProperties, selfState, principalId);
    }

    /**
     * Builds the provenance of a command the action sends: the row's id, the action's index, and the ontology version
     * the row was stamped with. A row without the stamp cannot give its commands provenance, so it is refused.
     *
     * @throws IllegalStateException if the row has no {@code ontologyVersion} stamp, the stamp is not a number, or the
     *     stamp's {@code longValue()} is outside the {@code int} range
     */
    private static CommandProvenance provenance(StoreEventRow sourceRow, int actionIndex) {
        Object stamp = sourceRow.payload().get("ontologyVersion");
        if (stamp == null) {
            throw new IllegalStateException("TransitionFired row " + sourceRow.id()
                    + " has no ontologyVersion stamp, so its actions cannot carry provenance");
        }
        if (!(stamp instanceof Number version)) {
            throw new IllegalStateException("TransitionFired row " + sourceRow.id()
                    + " has an ontologyVersion stamp that is not a number: " + stamp);
        }
        long longVersion = version.longValue();
        if (longVersion < Integer.MIN_VALUE || longVersion > Integer.MAX_VALUE) {
            throw new IllegalStateException("TransitionFired row " + sourceRow.id()
                    + " has an ontologyVersion stamp outside the int range: " + stamp);
        }
        return new CommandProvenance(sourceRow.id(), actionIndex, (int) longVersion);
    }

    /**
     * Derives a {@code CreateObject} action's new object id deterministically from the row's own id and its
     * {@code actionIndex}, so a retried call resolves to the exact same id every time and the existence check in
     * {@link #commitCreateObject} can detect a duplicate.
     */
    private static ObjectId derivedCreateId(UUID rowId, int actionIndex) {
        return new ObjectId(
                UUID.nameUUIDFromBytes((rowId.toString() + ":" + actionIndex).getBytes(StandardCharsets.UTF_8)));
    }
}
