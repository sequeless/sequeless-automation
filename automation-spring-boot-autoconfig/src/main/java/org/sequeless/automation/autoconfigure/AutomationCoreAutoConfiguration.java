package org.sequeless.automation.autoconfigure;

import org.sequeless.automation.api.ActionExecutor;
import org.sequeless.automation.api.ObjectReadPort;
import org.sequeless.automation.api.StoreCommandPort;
import org.sequeless.automation.api.TriggerEvaluator;
import org.sequeless.automation.core.DefaultActionExecutor;
import org.sequeless.automation.core.DefaultTriggerEvaluator;
import org.sequeless.expression.api.ExpressionPort;
import org.sequeless.ontology.behaviour.spi.BehaviourSource;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Wires the automation core's two beans, {@link ActionExecutor} and {@link TriggerEvaluator}, from the ports the host
 * supplies. It has no property switch: the core is wired whenever its ports are present, whichever adapter is
 * selected.
 *
 * <p>Each bean is guarded twice. {@link ConditionalOnBean} skips it unless the host has supplied every port it
 * needs, so a missing port leaves the bean absent rather than failing the context. {@link ConditionalOnMissingBean}
 * lets a host replace the core implementation with its own bean.
 *
 * <p>The host-supplied types are {@link ExpressionPort}, {@link ObjectReadPort}, {@link StoreCommandPort},
 * {@link SchemaSource} and {@link BehaviourSource}. A {@link ConditionalOnBean} check is evaluated when this class is
 * processed, so each autoconfiguration that supplies a port must be processed first. Auto-configurations are otherwise
 * sorted by class name, and this package sorts before the expression package, so {@link AutoConfigureAfter} names the
 * expression autoconfiguration by string. The name keeps this module free of a compile dependency on it. No ontology
 * autoconfiguration exists, so {@link SchemaSource} and {@link BehaviourSource} are supplied by the host, with no
 * ordering declared for them.
 */
@AutoConfiguration
@AutoConfigureAfter(name = {"org.sequeless.expression.autoconfigure.JexlExpressionAutoConfiguration"})
public class AutomationCoreAutoConfiguration {

    /**
     * @param expressionPort the expression port the executor renders templates through
     * @param readPort the read-only view of object state the executor reads through
     * @param commandPort the Store command port the executor's writes go through
     * @return a new {@link DefaultActionExecutor} over these ports
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({ExpressionPort.class, ObjectReadPort.class, StoreCommandPort.class})
    public ActionExecutor actionExecutor(
            ExpressionPort expressionPort, ObjectReadPort readPort, StoreCommandPort commandPort) {
        return new DefaultActionExecutor(expressionPort, readPort, commandPort);
    }

    /**
     * @param schemaSource the schema source the evaluator reads each tenant's schema from
     * @param behaviourSource the behaviour source the evaluator reads each tenant's behaviour from
     * @param readPort the read-only view of object state the evaluator reads through
     * @param commandPort the Store command port the evaluator fires transitions through
     * @return a new {@link DefaultTriggerEvaluator} over these ports
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({SchemaSource.class, BehaviourSource.class, ObjectReadPort.class, StoreCommandPort.class})
    public TriggerEvaluator triggerEvaluator(
            SchemaSource schemaSource,
            BehaviourSource behaviourSource,
            ObjectReadPort readPort,
            StoreCommandPort commandPort) {
        return new DefaultTriggerEvaluator(schemaSource, behaviourSource, readPort, commandPort);
    }
}
