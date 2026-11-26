/**
 * The automation API: the types and ports that automation adapters and the automation core share.
 *
 * <p>The API imports nothing from the Store, from the Store modules, or from the prototype it was moved
 * out of. It depends only on the kernel value and identity types and on the ontology automation contract.
 * Contract types ({@code org.sequeless.ontology.automation.api}, such as {@code Action} and {@code
 * WebhookAction}) are used directly, never copied or re-exported.
 *
 * <p>The package holds the Store event types ({@link org.sequeless.automation.api.StoreEvent}, {@link
 * org.sequeless.automation.api.ObjectChangedEvent}, {@link org.sequeless.automation.api.TransitionFiredEvent}),
 * the inbound ports ({@link org.sequeless.automation.api.TriggerEvaluator}, {@link
 * org.sequeless.automation.api.ActionExecutor}), the outbound ports ({@link
 * org.sequeless.automation.api.AutomationPort}, {@link org.sequeless.automation.api.ObjectReadPort}, {@link
 * org.sequeless.automation.api.StoreCommandPort}), and the commands sent to the Store. ObjectReadPort and
 * StoreCommandPort are the read and write ports to the Store; ADR 0002 is the decision record for them.
 */
package org.sequeless.automation.api;
