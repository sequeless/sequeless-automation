package org.sequeless.automation.autoconfigure;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.sequeless.automation.api.ActionExecutor;
import org.sequeless.automation.api.AutomationPort;
import org.sequeless.automation.api.TriggerEvaluator;
import org.sequeless.automation.inprocess.InProcessAutomationPort;
import org.sequeless.automation.inprocess.InProcessAutomationProperties;
import org.sequeless.automation.inprocess.InProcessTimerScheduler;
import org.sequeless.ontology.automation.spi.AutomationSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires {@link InProcessAutomationPort} when {@code sequeless.automation.adapter} is {@code inprocess}.
 *
 * <p>The class is guarded by {@link ConditionalOnClass} as well as by the property. Without the in-process adapter
 * on the classpath, Spring never loads this class, so the module starts without it.
 *
 * <p>The beans are the adapter's properties (bound under {@link InProcessAutomationProperties#PREFIX}), a {@link Clock}
 * the timer scheduler measures time against, the {@link InProcessTimerScheduler}, and the {@link AutomationPort}. A
 * single-thread executor runs {@link InProcessTimerScheduler#tick()} at a fixed delay of
 * {@link InProcessAutomationProperties#getTimerTickMs()}. The executor is shut down when the context closes.
 *
 * <p>The action executor and the trigger evaluator come from {@link AutomationCoreAutoConfiguration}, and the
 * {@link AutomationSource} comes from the host.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "sequeless.automation.adapter", havingValue = "inprocess")
@ConditionalOnClass(InProcessAutomationPort.class)
@EnableConfigurationProperties
public class InProcessAutomationAutoConfiguration {

    /**
     * @return the adapter's properties, bound from {@code sequeless.automation.inprocess.*}
     */
    @Bean
    @ConfigurationProperties(prefix = InProcessAutomationProperties.PREFIX)
    public InProcessAutomationProperties inProcessAutomationProperties() {
        return new InProcessAutomationProperties();
    }

    /**
     * The clock the timer scheduler measures "now" against. {@link ConditionalOnMissingBean} lets a host, or a test,
     * register its own {@link Clock}.
     *
     * @return {@link Clock#systemUTC()}
     */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * The single-thread executor the timer tick runs on. {@code destroyMethod = "shutdownNow"} stops it when the
     * context closes. The thread is a daemon, so it never keeps the JVM alive.
     *
     * @return a new single-thread scheduled executor
     */
    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService timerTickExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "inprocess-timer-tick");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Creates the timer scheduler and schedules its {@link InProcessTimerScheduler#tick()} on
     * {@code timerTickExecutor} at a fixed delay of {@link InProcessAutomationProperties#getTimerTickMs()}. The method
     * schedules {@code tick}, not {@code sweep}: {@code tick} keeps the schedule alive when one run fails.
     *
     * @param triggerEvaluator the evaluator a due timer is fired on
     * @param clock the clock the scheduler measures "now" against
     * @param properties the bound adapter properties, supplying the tick interval
     * @param timerTickExecutor the executor the tick is scheduled on
     * @return the scheduler, whose tick is already scheduled
     */
    @Bean
    public InProcessTimerScheduler inProcessTimerScheduler(
            TriggerEvaluator triggerEvaluator,
            Clock clock,
            InProcessAutomationProperties properties,
            ScheduledExecutorService timerTickExecutor) {
        InProcessTimerScheduler scheduler = new InProcessTimerScheduler(clock, triggerEvaluator);
        long tickMs = properties.getTimerTickMs();
        timerTickExecutor.scheduleWithFixedDelay(scheduler::tick, tickMs, tickMs, TimeUnit.MILLISECONDS);
        return scheduler;
    }

    /**
     * @param actionExecutor the action executor the port applies each action through
     * @param properties the bound adapter properties, supplying the webhook retry configuration
     * @param triggerEvaluator the evaluator an object-lifecycle or timer entry is routed to
     * @param timerScheduler the scheduler the timers a {@code TransitionFired} row starts are scheduled on
     * @param automationSource the source a {@code TransitionFired} row's actions are read from
     * @return a new {@link InProcessAutomationPort} over these
     */
    @Bean
    public AutomationPort automationPort(
            ActionExecutor actionExecutor,
            InProcessAutomationProperties properties,
            TriggerEvaluator triggerEvaluator,
            InProcessTimerScheduler timerScheduler,
            AutomationSource automationSource) {
        return new InProcessAutomationPort(
                actionExecutor, properties, triggerEvaluator, timerScheduler, automationSource);
    }
}
