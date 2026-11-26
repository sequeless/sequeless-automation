package org.sequeless.automation.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.sequeless.automation.api.ActionExecutor;
import org.sequeless.automation.api.ObjectReadPort;
import org.sequeless.automation.api.StoreCommandPort;
import org.sequeless.automation.api.TriggerEvaluator;
import org.sequeless.automation.core.DefaultActionExecutor;
import org.sequeless.automation.core.DefaultTriggerEvaluator;
import org.sequeless.automation.testkit.RecordingActionExecutor;
import org.sequeless.expression.api.ExpressionPort;
import org.sequeless.expression.autoconfigure.JexlExpressionAutoConfiguration;
import org.sequeless.ontology.behaviour.spi.BehaviourSource;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Exercises {@link AutomationCoreAutoConfiguration} through an {@link ApplicationContextRunner}. */
class AutomationCoreAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AutomationCoreAutoConfiguration.class));

    @Test
    void wiresTheCoreImplementationsWhenEveryHostPortIsPresent() {
        HostPorts.add(runner).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ActionExecutor.class)).isInstanceOf(DefaultActionExecutor.class);
            assertThat(context.getBean(TriggerEvaluator.class)).isInstanceOf(DefaultTriggerEvaluator.class);
        });
    }

    @Test
    void omitsTheActionExecutorWhenTheExpressionPortIsMissing() {
        HostPorts.add(runner, ExpressionPort.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ActionExecutor.class);
            assertThat(context).hasSingleBean(TriggerEvaluator.class);
        });
    }

    @Test
    void omitsTheTriggerEvaluatorWhenTheSchemaSourceIsMissing() {
        HostPorts.add(runner, SchemaSource.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(TriggerEvaluator.class);
            assertThat(context).hasSingleBean(ActionExecutor.class);
        });
    }

    @Test
    void omitsTheTriggerEvaluatorWhenTheBehaviourSourceIsMissing() {
        HostPorts.add(runner, BehaviourSource.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(TriggerEvaluator.class);
        });
    }

    @Test
    void omitsBothBeansWhenTheReadPortIsMissing() {
        HostPorts.add(runner, ObjectReadPort.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ActionExecutor.class);
            assertThat(context).doesNotHaveBean(TriggerEvaluator.class);
        });
    }

    @Test
    void omitsBothBeansWhenTheCommandPortIsMissing() {
        HostPorts.add(runner, StoreCommandPort.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ActionExecutor.class);
            assertThat(context).doesNotHaveBean(TriggerEvaluator.class);
        });
    }

    @Test
    void aHostSuppliedActionExecutorReplacesTheCoreOne() {
        HostPorts.add(runner)
                .withBean(ActionExecutor.class, RecordingActionExecutor::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ActionExecutor.class);
                    assertThat(context.getBean(ActionExecutor.class)).isInstanceOf(RecordingActionExecutor.class);
                });
    }

    /**
     * The expression port comes from another autoconfiguration, which the core must be ordered after. The helper is listed
     * first here, but {@link AutoConfigurations} sorts by class name, and the core's name sorts before the expression
     * package. Without {@code @AutoConfigureAfter} on the core, its {@code ConditionalOnBean(ExpressionPort)} would find
     * no bean, and no {@link ActionExecutor} would be wired.
     */
    @Test
    void wiresTheActionExecutorWhenTheExpressionPortComesFromAnotherAutoconfiguration() {
        HostPorts.add(
                        new ApplicationContextRunner()
                                .withConfiguration(AutoConfigurations.of(
                                        JexlExpressionAutoConfiguration.class, AutomationCoreAutoConfiguration.class)),
                        ExpressionPort.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ExpressionPort.class);
                    assertThat(context).hasSingleBean(ActionExecutor.class);
                    assertThat(context.getBean(ActionExecutor.class)).isInstanceOf(DefaultActionExecutor.class);
                });
    }
}
