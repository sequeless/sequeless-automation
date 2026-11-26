package org.sequeless.automation.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.client.WorkflowClient;
import org.junit.jupiter.api.Test;
import org.sequeless.automation.api.ActionExecutor;
import org.sequeless.automation.api.AutomationPort;
import org.sequeless.automation.inprocess.InProcessAutomationPort;
import org.sequeless.automation.temporal.TemporalAutomationPort;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Proves the module starts with one adapter on the classpath, or with neither. The adapters are optional dependencies,
 * so a consumer has only the one it selects. Here the test classpath still holds both, so {@link FilteredClassLoader}
 * hides the classes of the adapter a case leaves out. The context then sees the classpath a consumer would have.
 *
 * <p>The three autoconfigurations are all registered in every case. Each one must be skipped cleanly when its adapter
 * is hidden, and no case may fail to start.
 */
class OneAdapterAtATimeTest {

    private static final String SELECT_IN_PROCESS = "sequeless.automation.adapter=inprocess";
    private static final String SELECT_TEMPORAL = "sequeless.automation.adapter=temporal";

    private final ApplicationContextRunner runner = HostPorts.add(new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AutomationCoreAutoConfiguration.class,
                    InProcessAutomationAutoConfiguration.class,
                    TemporalAutomationAutoConfiguration.class)));

    @Test
    void startsWithOnlyTheInProcessAdapterOnTheClasspath() {
        runner.withClassLoader(new FilteredClassLoader(TemporalAutomationPort.class, WorkflowClient.class))
                .withPropertyValues(SELECT_IN_PROCESS)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AutomationPort.class)).isInstanceOf(InProcessAutomationPort.class);
                    assertThat(context).hasSingleBean(ActionExecutor.class);
                });
    }

    @Test
    void startsWithOnlyTheTemporalAdapterOnTheClasspath() {
        runner.withClassLoader(new FilteredClassLoader(InProcessAutomationPort.class))
                .withPropertyValues(SELECT_TEMPORAL)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AutomationPort.class)).isInstanceOf(TemporalAutomationPort.class);
                    assertThat(context).hasSingleBean(ActionExecutor.class);
                });
    }

    @Test
    void startsWithNeitherAdapterOnTheClasspathWhenTemporalIsSelected() {
        runner.withClassLoader(new FilteredClassLoader(
                        TemporalAutomationPort.class, WorkflowClient.class, InProcessAutomationPort.class))
                .withPropertyValues(SELECT_TEMPORAL)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AutomationPort.class);
                    assertThat(context).hasSingleBean(ActionExecutor.class);
                });
    }

    @Test
    void startsWithNeitherAdapterOnTheClasspathWhenInProcessIsSelected() {
        runner.withClassLoader(new FilteredClassLoader(
                        TemporalAutomationPort.class, WorkflowClient.class, InProcessAutomationPort.class))
                .withPropertyValues(SELECT_IN_PROCESS)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AutomationPort.class);
                    assertThat(context).hasSingleBean(ActionExecutor.class);
                });
    }
}
