/**
 * The in-process automation adapter: an {@code AutomationPort} that applies a {@code TransitionFired} Store
 * event action by action, synchronously, on the calling thread, in the automation's index order, through the
 * matching {@code ActionExecutor} method. It stands in for a durable engine in tests and local development.
 *
 * <p>{@link org.sequeless.automation.inprocess.InProcessAutomationPort} is the port.
 * {@link org.sequeless.automation.inprocess.InProcessTimerScheduler} holds the timers a fired transition starts
 * in memory. {@link org.sequeless.automation.inprocess.InProcessAutomationProperties} carries the webhook retry
 * configuration, and {@link org.sequeless.automation.inprocess.WebhookDeliveryException} reports a delivery that
 * failed every attempt.
 *
 * <p>The adapter depends on {@code automation-api} and the ontology automation contract in main code, and on no
 * other automation module. It never depends on {@code automation-core}. Its webhook delivery uses the JDK's own
 * {@code java.net.http.HttpClient}, with a small fixed retry loop. The adapter owns idempotency itself, through
 * in-memory sets keyed by Store event id and action index, and those sets are not crash-safe.
 *
 * <p>This package has no Spring code. The autoconfiguration that wires the port, the timer scheduler and the
 * properties lives in {@code automation-spring-boot-autoconfig} (package {@code org.sequeless.automation.autoconfigure}).
 */
package org.sequeless.automation.inprocess;
