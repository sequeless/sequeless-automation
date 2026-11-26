package org.sequeless.automation.api;

import org.sequeless.kernel.identity.Scope;

/**
 * The port automation uses to send commands to the Store. The host binds it in-process to the Store's
 * inbound command port. The binding joins the caller's claim transaction and never opens a new one, so a
 * command and the claim that issued it commit or roll back together. The Store runs each command as the
 * automation principal, so its authorization and validation apply.
 *
 * <p>{@link #setProperty} and {@link #createObject} are keyed by their command's {@link CommandProvenance}:
 * the {@code sourceEventId}, which is the id of the claim row that issued the command, and the
 * {@code actionIndex}. A repeat with the same key is a no-op. A rolled-back claim releases its keys, so
 * the action runs again without applying twice.
 *
 * <p>{@link #fireTransition} carries no {@code CommandProvenance}, so it has no key of its own. Its
 * idempotency comes from its availability guard: a transition that is no longer available from the
 * object's current state returns {@code false}, so a repeat is typically a silent no-op. A self-loop
 * transition, whose source and target state are the same, can fire again on a repeat.
 *
 * <p>If the Store rejects a command, the method throws {@link StoreCommandRejectedException} and the
 * action is not partly applied.
 */
public interface StoreCommandPort {

    /**
     * Sets one property of an existing object.
     *
     * @param scope the tenant and principal to act as; must not be {@code null}
     * @param command the command to send; must not be {@code null}
     * @throws StoreCommandRejectedException if the Store rejects the command
     */
    void setProperty(Scope scope, SetPropertyCommand command);

    /**
     * Creates one object.
     *
     * @param scope the tenant and principal to act as; must not be {@code null}
     * @param command the command to send; must not be {@code null}
     * @throws StoreCommandRejectedException if the Store rejects the command
     */
    void createObject(Scope scope, CreateObjectCommand command);

    /**
     * Fires one transition on an object.
     *
     * @param scope the tenant and principal to act as; must not be {@code null}
     * @param command the command to send; must not be {@code null}
     * @return {@code true} if the transition committed; {@code false} if it was not available, which is a
     *     silent no-op
     * @throws StoreCommandRejectedException if the Store rejects the command
     */
    boolean fireTransition(Scope scope, FireTransitionCommand command);
}
