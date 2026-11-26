/**
 * The automation core: the reference implementation of the automation API's inbound ports,
 * {@link org.sequeless.automation.api.ActionExecutor} and {@link org.sequeless.automation.api.TriggerEvaluator}.
 *
 * <p>The module depends on {@code automation-api} and {@code expression-api}, plus the ontology contracts
 * it reads the schema and behaviour from. It never depends on store-core. Every read goes through
 * {@link org.sequeless.automation.api.ObjectReadPort} and every write through {@link
 * org.sequeless.automation.api.StoreCommandPort}, and the host builds the Store's candidate objects,
 * audit records and outbox rows from the commands this module sends.
 */
package org.sequeless.automation.core;
