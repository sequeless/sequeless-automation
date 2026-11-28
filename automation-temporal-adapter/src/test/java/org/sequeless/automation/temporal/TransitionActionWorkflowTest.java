package org.sequeless.automation.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.sun.net.httpserver.HttpServer;
import io.temporal.activity.ActivityOptions;
import io.temporal.api.enums.v1.RetryState;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sequeless.automation.api.AutomationFailure;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.api.CreateObjectAction;
import org.sequeless.ontology.automation.api.LogAction;
import org.sequeless.ontology.automation.api.SetPropertyAction;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.ontology.automation.api.WebhookAction;
import org.sequeless.ontology.schema.api.OntologyVersion;

/**
 * Covers {@link TransitionActionWorkflowImpl} and {@link ActionActivitiesImpl#applyTransitionAction}
 * in an in-memory {@link TestWorkflowEnvironment}. The workflow is started directly through the
 * environment's client. {@link TemporalAutomationPort} checks the stamp and throws before any workflow
 * starts, so going through it would never reach the activity's own checks, which is what this test is
 * for.
 *
 * <p>The activity-level stamp and index failures are non-retryable, so each one makes exactly one
 * attempt. A control case makes every call throw a plain {@link RuntimeException}, which Temporal
 * retries up to the configured maximum, so the attempt counter is shown to detect retries.
 */
class TransitionActionWorkflowTest {

    private static final String TASK_QUEUE = "sequeless-transition-actions-test";
    private static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));
    private static final String MACHINE_IRI = "http://example.org/ProjectLifecycle";
    private static final String DRAFT_IRI = "http://example.org/Draft";
    private static final String ACTIVE_IRI = "http://example.org/Active";
    private static final String STATUS_IRI = "http://example.org/status";
    private static final String TRANSITION_NAME = "activate";
    private static final String HOOK_PATH = "/hook";
    private static final String FAILING_HOOK_PATH = "/failing-hook";

    private static final AtomicInteger webhookHits = new AtomicInteger();
    private static final AtomicInteger failingWebhookHits = new AtomicInteger();
    private static HttpServer webhookServer;
    private static RecordingTransitionExecutor executor;
    private static RecordingAutomationFailureRecorder recorder;
    private static TestWorkflowEnvironment env;

    @BeforeAll
    static void startEnvironment() throws IOException {
        // Published before env.start(), as the Spring autoconfiguration does. The StartToClose
        // timeout is required: without it the test server refuses the activity, which fails the workflow
        // task and so retries forever, not the workflow.
        TemporalActivityOptions.publish(ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(3)
                        .setInitialInterval(Duration.ofMillis(10))
                        .setBackoffCoefficient(1.0)
                        .build())
                .build());

        webhookServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        webhookServer.createContext(HOOK_PATH, exchange -> {
            exchange.getRequestBody().readAllBytes();
            webhookHits.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        webhookServer.createContext(FAILING_HOOK_PATH, exchange -> {
            exchange.getRequestBody().readAllBytes();
            failingWebhookHits.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        webhookServer.start();

        executor = new RecordingTransitionExecutor();
        executor.setWebhookUrl("http://localhost:" + webhookServer.getAddress().getPort() + HOOK_PATH);

        recorder = new RecordingAutomationFailureRecorder();

        env = TestWorkflowEnvironment.newInstance();
        Worker worker = env.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(TransitionActionWorkflowImpl.class);
        worker.registerActivitiesImplementations(
                new ActionActivitiesImpl(executor, new SwappableAutomationSource(), recorder));
        env.start();
    }

    @AfterAll
    static void stopEnvironment() {
        env.close();
        webhookServer.stop(0);
    }

    @BeforeEach
    void resetDoubles() {
        SwappableAutomationSource.reset();
        executor.reset();
        recorder.reset();
        webhookHits.set(0);
        failingWebhookHits.set(0);
    }

    @Test
    void recordsNothingWhenEveryActionSucceeds() {
        SwappableAutomationSource.current = automationAt(1, new LogAction("project activated"));
        StoreEventRow entry = transitionFired(1);

        runToCompletion(entry, 0);

        assertThat(recorder.failures()).isEmpty();
    }

    @Test
    void recordsAWebhookDeliveryFailureOnceAfterItsLastAttempt() {
        WebhookAction webhook = new WebhookAction("https://example.invalid/hook", "POST", Optional.empty());
        SwappableAutomationSource.current = automationAt(1, webhook);
        executor.setWebhookUrl("http://localhost:" + webhookServer.getAddress().getPort() + FAILING_HOOK_PATH);
        StoreEventRow entry = transitionFired(1);

        try {
            ApplicationFailure failure =
                    failureOf(runToFailure(entry, 0), RetryState.RETRY_STATE_MAXIMUM_ATTEMPTS_REACHED);

            assertThat(failure.getType()).isEqualTo(WebhookActivityException.class.getName());
            assertThat(failingWebhookHits.get()).isEqualTo(3);
            assertThat(recorder.failures()).hasSize(1);
            AutomationFailure recorded = recorder.failures().get(0);
            assertThat(recorded.workflowId()).isEqualTo(entry.id() + ":0");
            assertThat(recorded.workflowType()).isEqualTo("TransitionActionWorkflow");
            assertThat(recorded.transition()).isEqualTo(new TransitionRef(MACHINE_IRI, DRAFT_IRI, TRANSITION_NAME));
            assertThat(recorded.cause())
                    .startsWith(WebhookActivityException.class.getName() + ": Webhook delivery to ")
                    .endsWith("received non-2xx status 500");
        } finally {
            executor.setWebhookUrl(
                    "http://localhost:" + webhookServer.getAddress().getPort() + HOOK_PATH);
        }
    }

    @Test
    void recordsAFinalWebhookResolveFailureAndStillFailsTheWorkflow() {
        WebhookAction webhook = new WebhookAction("https://example.invalid/hook", "POST", Optional.empty());
        SwappableAutomationSource.current = automationAt(1, webhook);
        executor.setFailResolve(true);
        StoreEventRow entry = transitionFired(1);

        ApplicationFailure failure = failureOf(runToFailure(entry, 0), RetryState.RETRY_STATE_MAXIMUM_ATTEMPTS_REACHED);

        assertThat(failure.getType()).isEqualTo("java.lang.RuntimeException");
        assertThat(recorder.failures()).hasSize(1);
        assertThat(recorder.failures().get(0).cause())
                .isEqualTo("java.lang.RuntimeException: RecordingTransitionExecutor is set to fail resolving");
        assertThat(recorder.failures().get(0).workflowId()).isEqualTo(entry.id() + ":0");
    }

    @Test
    void stillFailsTheWorkflowWhenTheRecorderItselfFails() {
        SwappableAutomationSource.current = automationAt(2, new LogAction("project activated"));
        recorder.setFailRecording(true);
        StoreEventRow entry = transitionFired(1);

        WorkflowFailedException failed = runToFailure(entry, 0);

        ApplicationFailure failure = failureOf(failed, RetryState.RETRY_STATE_NON_RETRYABLE_FAILURE);
        assertThat(failure.getType()).isEqualTo("TransitionFiredStampMismatch");
        assertThat(recorder.failures()).isEmpty();
    }

    @Test
    void appliesEachNonWebhookActionAtItsOwnIndex() {
        Action log = new LogAction("project activated");
        Action createObject = new CreateObjectAction("http://example.org/Task", List.of());
        Action setProperty = new SetPropertyAction(STATUS_IRI, Optional.empty(), Optional.of("self.owner"));
        SwappableAutomationSource.current = automationAt(1, log, createObject, setProperty);
        StoreEventRow entry = transitionFired(1);

        runToCompletion(entry, 0);
        runToCompletion(entry, 1);
        runToCompletion(entry, 2);

        assertThat(executor.calls())
                .containsExactly(
                        new RecordingTransitionExecutor.Call(
                                RecordingTransitionExecutor.METHOD_APPLY_ACTION, 0, entry.id(), log),
                        new RecordingTransitionExecutor.Call(
                                RecordingTransitionExecutor.METHOD_APPLY_ACTION, 1, entry.id(), createObject),
                        new RecordingTransitionExecutor.Call(
                                RecordingTransitionExecutor.METHOD_APPLY_ACTION, 2, entry.id(), setProperty));
    }

    @Test
    void resolvesAndDeliversTheWebhookActionAtItsIndex() {
        WebhookAction webhook = new WebhookAction("https://example.invalid/hook", "POST", Optional.empty());
        SwappableAutomationSource.current = automationAt(1, new LogAction("project activated"), webhook);
        StoreEventRow entry = transitionFired(1);

        runToCompletion(entry, 1);

        assertThat(executor.calls())
                .containsExactly(new RecordingTransitionExecutor.Call(
                        RecordingTransitionExecutor.METHOD_RESOLVE_WEBHOOK_ACTION, 1, entry.id(), webhook));
        assertThat(webhookHits.get()).isEqualTo(1);
    }

    @Test
    void refusesARowWithNoOntologyVersionStampWithANonRetryableFailure() {
        SwappableAutomationSource.current = automationAt(1, new LogAction("project activated"));
        StoreEventRow entry = transitionFired(null);

        assertNonRetryableFailure(
                entry,
                0,
                "TransitionFiredStampMissing",
                "TransitionFired row " + entry.id() + " has no ontologyVersion stamp");
    }

    @Test
    void refusesANonNumberStampWithANonRetryableFailure() {
        SwappableAutomationSource.current = automationAt(1, new LogAction("project activated"));
        StoreEventRow entry = transitionFired("1");

        assertNonRetryableFailure(
                entry,
                0,
                "TransitionFiredStampMissing",
                "TransitionFired row " + entry.id() + " has no ontologyVersion stamp");
    }

    @Test
    void refusesAStampThatDiffersFromTheAutomationsVersionWithANonRetryableFailure() {
        SwappableAutomationSource.current = automationAt(2, new LogAction("project activated"));
        StoreEventRow entry = transitionFired(1);

        assertNonRetryableFailure(
                entry,
                0,
                "TransitionFiredStampMismatch",
                "Automation is at ontology version 2 but TransitionFired row " + entry.id()
                        + " was written at version 1");
    }

    @Test
    void refusesAnIndexPastTheEndOfTheListWithANonRetryableFailure() {
        SwappableAutomationSource.current = automationAt(1, new LogAction("project activated"));
        StoreEventRow entry = transitionFired(1);

        assertNonRetryableFailure(
                entry,
                1,
                "TransitionFiredIndexOutOfRange",
                "TransitionFired row " + entry.id() + " has no action at index 1");
    }

    @Test
    void refusesANegativeIndexWithANonRetryableFailure() {
        SwappableAutomationSource.current = automationAt(1, new LogAction("project activated"));
        StoreEventRow entry = transitionFired(1);

        assertNonRetryableFailure(
                entry,
                -1,
                "TransitionFiredIndexOutOfRange",
                "TransitionFired row " + entry.id() + " has no action at index -1");
    }

    @Test
    void retriesAnActivityThatKeepsThrowingUntilItsMaximumAttempts() {
        SwappableAutomationSource.current = automationAt(1, new LogAction("project activated"));
        SwappableAutomationSource.failEveryCall = true;
        StoreEventRow entry = transitionFired(1);

        ApplicationFailure failure = failureOf(runToFailure(entry, 0), RetryState.RETRY_STATE_MAXIMUM_ATTEMPTS_REACHED);

        assertThat(failure.getType()).isEqualTo("java.lang.RuntimeException");
        assertThat(failure.isNonRetryable()).isFalse();
        assertThat(SwappableAutomationSource.calls.get()).isEqualTo(3);
        // Recorded once, after the last attempt, not once per attempt.
        assertRecordedOnce(entry, 0, "java.lang.RuntimeException: SwappableAutomationSource is set to fail every call");
    }

    /**
     * Runs the workflow for {@code entry} at {@code index}, asserts it failed with a non-retryable
     * {@link ApplicationFailure} of {@code type} whose message is exactly {@code message}, and asserts
     * that the activity made one attempt and applied nothing.
     */
    private static void assertNonRetryableFailure(StoreEventRow entry, int index, String type, String message) {
        ApplicationFailure failure =
                failureOf(runToFailure(entry, index), RetryState.RETRY_STATE_NON_RETRYABLE_FAILURE);

        assertThat(failure.getType()).isEqualTo(type);
        assertThat(failure.isNonRetryable()).isTrue();
        assertThat(failure.getOriginalMessage()).isEqualTo(message);
        assertThat(SwappableAutomationSource.calls.get()).isEqualTo(1);
        assertThat(executor.calls()).isEmpty();
        assertRecordedOnce(entry, index, type + ": " + message);
    }

    /** Asserts the recorder got exactly one failure for {@code entry} at {@code index}, with {@code cause}. */
    private static void assertRecordedOnce(StoreEventRow entry, int index, String cause) {
        assertThat(recorder.failures()).hasSize(1);
        AutomationFailure recorded = recorder.failures().get(0);
        assertThat(recorded.workflowId()).isEqualTo(entry.id() + ":" + index);
        assertThat(recorded.workflowType()).isEqualTo("TransitionActionWorkflow");
        assertThat(recorded.scope()).isEqualTo(SCOPE);
        assertThat(recorded.cause()).isEqualTo(cause);
    }

    /**
     * Unwraps the workflow failure's chain, {@link WorkflowFailedException} then {@link ActivityFailure}
     * then {@link ApplicationFailure}, asserting the activity-level retry state on the way.
     */
    private static ApplicationFailure failureOf(WorkflowFailedException failed, RetryState expectedRetryState) {
        assertThat(failed.getCause()).isInstanceOf(ActivityFailure.class);
        ActivityFailure activityFailure = (ActivityFailure) failed.getCause();
        assertThat(activityFailure.getRetryState()).isEqualTo(expectedRetryState);
        assertThat(activityFailure.getCause()).isInstanceOf(ApplicationFailure.class);
        return (ApplicationFailure) activityFailure.getCause();
    }

    /** Starts the {@link TransitionActionWorkflow} for {@code entry} at {@code index} without waiting for it. */
    private static WorkflowStub startWorkflow(StoreEventRow entry, int index) {
        TransitionActionWorkflow workflow = env.getWorkflowClient()
                .newWorkflowStub(
                        TransitionActionWorkflow.class,
                        WorkflowOptions.newBuilder()
                                .setWorkflowId(entry.id() + ":" + index)
                                .setTaskQueue(TASK_QUEUE)
                                .build());
        WorkflowClient.start(workflow::execute, entry, index);
        return WorkflowStub.fromTyped(workflow);
    }

    /** Starts the workflow for {@code entry} at {@code index} and waits, bounded, for it to complete. */
    private static void runToCompletion(StoreEventRow entry, int index) {
        try {
            startWorkflow(entry, index).getResult(5, TimeUnit.SECONDS, Void.class);
        } catch (TimeoutException e) {
            throw new AssertionError("Workflow " + entry.id() + ":" + index + " did not close within 5 seconds", e);
        }
    }

    /**
     * Starts the workflow for {@code entry} at {@code index}, waits, bounded, for it to close, and
     * returns the {@link WorkflowFailedException} it closed with.
     */
    private static WorkflowFailedException runToFailure(StoreEventRow entry, int index) {
        WorkflowStub stub = startWorkflow(entry, index);
        Throwable thrown = catchThrowable(() -> stub.getResult(5, TimeUnit.SECONDS, Void.class));
        assertThat(thrown).isInstanceOf(WorkflowFailedException.class);
        return (WorkflowFailedException) thrown;
    }

    /** An automation at {@code ontologyVersion} whose transition has the given actions, in order. */
    private static Automation automationAt(int ontologyVersion, Action... actions) {
        return new Automation(
                new OntologyVersion(ontologyVersion),
                Map.of(new TransitionRef(MACHINE_IRI, DRAFT_IRI, TRANSITION_NAME), List.of(actions)));
    }

    /**
     * A {@code TransitionFired} row carrying the keys {@code DefaultTransitionService} writes on one. A
     * {@code null} stamp omits the {@code ontologyVersion} key, because a null payload value is rejected.
     */
    private static StoreEventRow transitionFired(Object ontologyVersion) {
        Map<String, Object> payload = new HashMap<>(Map.of(
                "objectId",
                UUID.randomUUID().toString(),
                "tenantId",
                SCOPE.tenantId().value(),
                "principalId",
                SCOPE.principal().id(),
                "transitionName",
                TRANSITION_NAME,
                "fromState",
                DRAFT_IRI,
                "toState",
                ACTIVE_IRI,
                "self",
                Map.of(),
                "typeIri",
                "http://example.org/Project",
                "machineIri",
                MACHINE_IRI));
        if (ontologyVersion != null) {
            payload.put("ontologyVersion", ontologyVersion);
        }
        return new StoreEventRow(
                UUID.randomUUID(), StoreEventRow.KIND_TRANSITION_FIRED, payload, Instant.parse("2026-01-01T00:00:00Z"));
    }
}
