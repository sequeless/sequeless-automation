/**
 * The durable {@code AutomationPort} adapter, backed by the Temporal Java SDK.
 *
 * <p>{@link org.sequeless.automation.temporal.TemporalAutomationPort#ingest} durably starts, for each
 * {@code TransitionFired} Store event, one {@link org.sequeless.automation.temporal.TransitionActionWorkflow}
 * execution per action of the fired transition, keyed {@code "<eventId>:<index>"}, so each action has its own
 * run and its own idempotency (Temporal's {@code WorkflowExecutionAlreadyStarted} semantics). Each execution
 * calls {@link org.sequeless.automation.temporal.ActionActivities#applyTransitionAction}, a thin wrapper over
 * the injected {@code ActionExecutor}. A webhook action also performs its HTTP call there, and Temporal's own
 * configurable {@code RetryOptions} retry it, rather than a hand-rolled retry loop.
 *
 * <p>Main code depends on {@code automation-api} and the kernel and ontology automation contract types. It
 * never depends on {@code automation-core}. The one exception is {@link
 * org.sequeless.automation.temporal.TemporalAutomationProperties}, which binds the {@code
 * sequeless.automation.temporal.*} configuration namespace with Spring Boot's {@code @ConfigurationProperties}
 * (an optional dependency).
 *
 * <p>The Spring autoconfiguration that wires this adapter lives in {@code automation-spring-boot-autoconfig}, not
 * here. It calls {@link org.sequeless.automation.temporal.TemporalActivityOptions#publish} before the worker
 * starts.
 *
 * <p><b>This module depends on {@code io.temporal:temporal-sdk} directly, never on
 * {@code temporal-spring-boot-starter}.</b> The starter pulls in Spring Boot 2.7 jars, which conflict with the
 * Spring Boot 4.1 line this build imports. {@code temporal-sdk} has no Spring dependency, so the adapter needs
 * none of the starter.
 *
 * <p><b>Workflow and activity type names come from the simple names of their Java interfaces.</b> Renaming an
 * interface, or moving it so that its simple name changes, orphans every running execution of it. The names
 * {@code ChangeEventWorkflow}, {@code TimerWorkflow}, {@code TransitionActionWorkflow}, {@code ActionActivities}
 * and {@code TriggerActivities} must not change.
 */
package org.sequeless.automation.temporal;
