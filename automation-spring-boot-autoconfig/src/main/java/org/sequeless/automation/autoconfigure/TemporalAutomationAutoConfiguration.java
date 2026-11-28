package org.sequeless.automation.autoconfigure;

import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.common.RetryOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import java.time.Duration;
import org.sequeless.automation.api.ActionExecutor;
import org.sequeless.automation.api.AutomationFailureRecorder;
import org.sequeless.automation.api.AutomationPort;
import org.sequeless.automation.api.TriggerEvaluator;
import org.sequeless.automation.temporal.ActionActivitiesImpl;
import org.sequeless.automation.temporal.ChangeEventWorkflowImpl;
import org.sequeless.automation.temporal.TemporalActivityOptions;
import org.sequeless.automation.temporal.TemporalAutomationPort;
import org.sequeless.automation.temporal.TemporalAutomationProperties;
import org.sequeless.automation.temporal.TimerWorkflowImpl;
import org.sequeless.automation.temporal.TransitionActionWorkflowImpl;
import org.sequeless.automation.temporal.TriggerActivitiesImpl;
import org.sequeless.ontology.automation.spi.AutomationSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires {@link TemporalAutomationPort} and its Temporal client and worker when {@code sequeless.automation.adapter} is
 * {@code temporal}.
 *
 * <p>The class is guarded by {@link ConditionalOnClass} as well as by the property. Without the Temporal adapter or the
 * Temporal SDK on the classpath, Spring never loads this class.
 *
 * <p><b>Built directly on the SDK, not on {@code temporal-spring-boot-starter}.</b> The starter pulls in Spring Boot 2.7,
 * which conflicts with the Spring Boot 4.1 line this module imports. {@code temporal-sdk} has no Spring dependency, so
 * this class builds {@link WorkflowServiceStubs}, {@link WorkflowClient} and {@link WorkerFactory} directly.
 *
 * <p>The action executor and the trigger evaluator come from {@link AutomationCoreAutoConfiguration}. The
 * {@link AutomationSource} comes from the host.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "sequeless.automation.adapter", havingValue = "temporal")
@ConditionalOnClass({TemporalAutomationPort.class, WorkflowClient.class})
@EnableConfigurationProperties
public class TemporalAutomationAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TemporalAutomationAutoConfiguration.class);

    /**
     * How long a single attempt of any one activity may run before Temporal considers that attempt timed out. This
     * bounds one attempt's wall-clock duration, which is unrelated to the retry policy's maximum attempts. No
     * configuration property exposes it.
     */
    private static final Duration ACTIVITY_START_TO_CLOSE_TIMEOUT = Duration.ofSeconds(30);

    /**
     * The configured {@code sequeless.automation.temporal.*} properties.
     *
     * @return the properties bean
     */
    @Bean
    @ConfigurationProperties(prefix = "sequeless.automation.temporal")
    public TemporalAutomationProperties temporalAutomationProperties() {
        return new TemporalAutomationProperties();
    }

    /**
     * gRPC stubs are constructed lazily and do not connect eagerly. This method returns even when no Temporal server is
     * reachable at the configured target, so the context can start without one.
     *
     * @param props the bound {@code sequeless.automation.temporal.*} configuration
     * @return stubs targeting {@code props.getTarget()} if set, else the local default ({@code 127.0.0.1:7233})
     */
    @Bean
    public WorkflowServiceStubs workflowServiceStubs(TemporalAutomationProperties props) {
        String target = props.getTarget();
        if (target == null || target.isBlank()) {
            return WorkflowServiceStubs.newLocalServiceStubs();
        }
        WorkflowServiceStubsOptions options =
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build();
        return WorkflowServiceStubs.newServiceStubs(options);
    }

    /**
     * @param stubs the service stubs the client is built on
     * @return a new workflow client
     */
    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(stubs);
    }

    /**
     * @param client the client {@link TemporalAutomationPort} starts workflow executions with
     * @param props the bound {@code sequeless.automation.temporal.*} configuration, supplying the task queue
     * @param automationSource the source the per-action path reads a {@code TransitionFired} row's automation from
     * @param triggerEvaluator the evaluator whose {@code timersStartedBy} resolves the timers a row starts
     * @return a new {@link TemporalAutomationPort}
     */
    @Bean
    public AutomationPort automationPort(
            WorkflowClient client,
            TemporalAutomationProperties props,
            AutomationSource automationSource,
            TriggerEvaluator triggerEvaluator) {
        return new TemporalAutomationPort(client, props.getTaskQueue(), automationSource, triggerEvaluator);
    }

    /**
     * Publishes the activity options (before any activity implementation is created), registers the workflow and
     * activity implementations, and starts a worker on the configured task queue.
     *
     * <p>Temporal creates a workflow implementation reflectively, once per execution, so it cannot receive the options
     * through a constructor. {@link TemporalActivityOptions#publish} is called once here, and every later execution
     * reads the same value.
     *
     * <p>{@link WorkerFactory#start()} is not lazy. It makes a blocking {@code DescribeNamespace} call and throws when no
     * server answers. This method logs a warning and returns the factory unstarted instead of failing the context. The
     * {@link #automationPort} still works, because starting workflows needs only the lazy client, so actions are queued
     * even while no worker polls the queue.
     *
     * @param client the Temporal client the worker factory is built from
     * @param actionExecutor the action executor the action activities call
     * @param triggerEvaluator the trigger evaluator the trigger activities call
     * @param automationSource the source the action activities read the automation from
     * @param failureRecorders the host's failure recorder, if it has one; without one, final failures are not recorded
     * @param props the bound configuration, supplying the task queue and the retry policy
     * @return the worker factory, kept as a bean so the context shuts it down
     */
    @Bean
    public WorkerFactory workerFactory(
            WorkflowClient client,
            ActionExecutor actionExecutor,
            TriggerEvaluator triggerEvaluator,
            AutomationSource automationSource,
            ObjectProvider<AutomationFailureRecorder> failureRecorders,
            TemporalAutomationProperties props) {
        TemporalActivityOptions.publish(buildActivityOptions(props.getRetry()));

        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(props.getTaskQueue());
        worker.registerWorkflowImplementationTypes(
                TransitionActionWorkflowImpl.class, ChangeEventWorkflowImpl.class, TimerWorkflowImpl.class);
        worker.registerActivitiesImplementations(
                new ActionActivitiesImpl(
                        actionExecutor,
                        automationSource,
                        failureRecorders.getIfAvailable(AutomationFailureRecorder::none)),
                new TriggerActivitiesImpl(triggerEvaluator));
        try {
            factory.start();
        } catch (RuntimeException e) {
            log.warn(
                    "Failed to start Temporal WorkerFactory for task queue '{}'; the application will continue starting, "
                            + "but no worker will poll this queue until it is restarted with connectivity to the Temporal "
                            + "server restored",
                    props.getTaskQueue(),
                    e);
        }
        return factory;
    }

    private static ActivityOptions buildActivityOptions(TemporalAutomationProperties.Retry retry) {
        RetryOptions retryOptions = RetryOptions.newBuilder()
                .setInitialInterval(retry.getInitialInterval())
                .setBackoffCoefficient(retry.getBackoffCoefficient())
                .setMaximumAttempts(retry.getMaximumAttempts())
                .build();
        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(ACTIVITY_START_TO_CLOSE_TIMEOUT)
                .setRetryOptions(retryOptions)
                .build();
    }
}
