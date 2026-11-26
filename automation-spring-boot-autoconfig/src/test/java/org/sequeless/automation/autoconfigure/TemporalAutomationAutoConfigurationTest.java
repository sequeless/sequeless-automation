package org.sequeless.automation.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sequeless.automation.api.ActionExecutor;
import org.sequeless.automation.api.AutomationPort;
import org.sequeless.automation.api.TriggerEvaluator;
import org.sequeless.automation.temporal.TemporalActivityOptions;
import org.sequeless.automation.temporal.TemporalAutomationPort;
import org.sequeless.automation.temporal.TemporalAutomationProperties;
import org.sequeless.automation.testkit.RecordingActionExecutor;
import org.sequeless.automation.testkit.RecordingTriggerEvaluator;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.spi.AutomationSource;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Exercises {@link TemporalAutomationAutoConfiguration} through an {@link ApplicationContextRunner}. The action and
 * trigger doubles stand in for the core, so this test needs only the autoconfiguration.
 *
 * <p><b>No Temporal server is needed.</b> The service stubs are lazy, so the client builds without a connection. The
 * worker factory's {@code start()} does connect: it fails with no server, and the autoconfiguration logs that failure
 * and returns the factory unstarted. That behaviour is why {@code hasNotFailed()} holds here. A test that needs a
 * running worker would need a server, and none is used in this module.
 */
class TemporalAutomationAutoConfigurationTest {

    private static final AutomationSource AUTOMATION_SOURCE =
            tenant -> new Automation(new OntologyVersion(1), Map.of());

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TemporalAutomationAutoConfiguration.class))
            .withBean(ActionExecutor.class, RecordingActionExecutor::new)
            .withBean(TriggerEvaluator.class, RecordingTriggerEvaluator::new)
            .withBean(AutomationSource.class, () -> AUTOMATION_SOURCE);

    @Test
    void wiresAutomationPortWhenAdapterPropertySetAndActionExecutorPresent() {
        runner.withPropertyValues("sequeless.automation.adapter=temporal").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AutomationPort.class);
            assertThat(context.getBean(AutomationPort.class)).isInstanceOf(TemporalAutomationPort.class);
            assertThat(context).hasSingleBean(WorkflowClient.class);
            assertThat(context).hasSingleBean(WorkflowServiceStubs.class);
            assertThat(context).hasSingleBean(WorkerFactory.class);
        });
    }

    @Test
    void doesNotWireWhenAdapterPropertyAbsent() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AutomationPort.class);
        });
    }

    @Test
    void doesNotWireWhenAdapterPropertyIsSomethingElse() {
        runner.withPropertyValues("sequeless.automation.adapter=other").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AutomationPort.class);
        });
    }

    @Test
    void bindsRetryAndTaskQueuePropertiesWithDefaults() {
        runner.withPropertyValues("sequeless.automation.adapter=temporal").run(context -> {
            assertThat(context).hasNotFailed();
            TemporalAutomationProperties props = context.getBean(TemporalAutomationProperties.class);
            assertThat(props.getTaskQueue()).isEqualTo("sequeless-actions");
            assertThat(props.getRetry().getMaximumAttempts()).isEqualTo(5);
        });
    }

    @Test
    void bindsCustomRetryAndTaskQueueProperties() {
        runner.withPropertyValues(
                        "sequeless.automation.adapter=temporal",
                        "sequeless.automation.temporal.task-queue=custom-queue",
                        "sequeless.automation.temporal.retry.initial-interval=2s",
                        "sequeless.automation.temporal.retry.backoff-coefficient=1.5",
                        "sequeless.automation.temporal.retry.maximum-attempts=7")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    TemporalAutomationProperties props = context.getBean(TemporalAutomationProperties.class);
                    assertThat(props.getTaskQueue()).isEqualTo("custom-queue");
                    assertThat(props.getRetry().getInitialInterval()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(props.getRetry().getBackoffCoefficient()).isEqualTo(1.5);
                    assertThat(props.getRetry().getMaximumAttempts()).isEqualTo(7);
                });
    }

    @Test
    void publishesActivityOptionsBuiltFromTheRetryProperties() throws ReflectiveOperationException {
        runner.withPropertyValues(
                        "sequeless.automation.adapter=temporal",
                        "sequeless.automation.temporal.retry.initial-interval=2s",
                        "sequeless.automation.temporal.retry.backoff-coefficient=1.5",
                        "sequeless.automation.temporal.retry.maximum-attempts=7")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ActivityOptions published = publishedActivityOptions();
                    assertThat(published).isNotNull();
                    assertThat(published.getStartToCloseTimeout()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(published.getRetryOptions().getInitialInterval()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(published.getRetryOptions().getBackoffCoefficient())
                            .isEqualTo(1.5);
                    assertThat(published.getRetryOptions().getMaximumAttempts()).isEqualTo(7);
                });
    }

    /**
     * Reads the activity options {@link TemporalActivityOptions#publish} stored. The holder's accessor is
     * package-private to the adapter, and this module's tests run in another package, so the field is read
     * reflectively. The field is the only place the published value is kept.
     */
    private static ActivityOptions publishedActivityOptions() throws ReflectiveOperationException {
        Field field = TemporalActivityOptions.class.getDeclaredField("ACTIVITY_OPTIONS");
        field.setAccessible(true);
        return (ActivityOptions) field.get(null);
    }
}
