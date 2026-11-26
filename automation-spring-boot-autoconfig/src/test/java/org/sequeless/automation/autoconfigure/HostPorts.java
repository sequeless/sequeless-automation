package org.sequeless.automation.autoconfigure;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.sequeless.automation.api.ObjectReadPort;
import org.sequeless.automation.api.StoreCommandPort;
import org.sequeless.automation.testkit.InMemoryObjectReadPort;
import org.sequeless.automation.testkit.InMemoryStoreCommandPort;
import org.sequeless.expression.api.ExpressionContext;
import org.sequeless.expression.api.ExpressionPort;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.spi.AutomationSource;
import org.sequeless.ontology.behaviour.spi.BehaviourSource;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The host-supplied beans the automation core needs, as fakes, for the autoconfiguration tests. The in-memory ports
 * come from the testkit. The expression port and the ontology sources are stubs: the autoconfiguration tests build
 * the core's beans but never run them, so the stubs throw if anything calls them.
 */
final class HostPorts {

    private HostPorts() {}

    /**
     * Adds the host beans to {@code runner}, leaving out the types named in {@code omitted}.
     *
     * @param runner the runner to add the beans to
     * @param omitted the host bean types to leave out, to test a missing port
     * @return a runner with the remaining host beans registered
     */
    static ApplicationContextRunner add(ApplicationContextRunner runner, Class<?>... omitted) {
        List<Class<?>> skip = Arrays.asList(omitted);
        ApplicationContextRunner result = runner;
        if (!skip.contains(ExpressionPort.class)) {
            result = result.withBean(ExpressionPort.class, UnusedExpressionPort::new);
        }
        if (!skip.contains(ObjectReadPort.class)) {
            result = result.withBean(ObjectReadPort.class, InMemoryObjectReadPort::new);
        }
        if (!skip.contains(StoreCommandPort.class)) {
            result = result.withBean(StoreCommandPort.class, InMemoryStoreCommandPort::new);
        }
        if (!skip.contains(SchemaSource.class)) {
            result = result.withBean(SchemaSource.class, () -> tenant -> {
                throw unused();
            });
        }
        if (!skip.contains(BehaviourSource.class)) {
            result = result.withBean(BehaviourSource.class, () -> tenant -> {
                throw unused();
            });
        }
        if (!skip.contains(AutomationSource.class)) {
            result = result.withBean(
                    AutomationSource.class, () -> tenant -> new Automation(new OntologyVersion(1), Map.of()));
        }
        return result;
    }

    private static UnsupportedOperationException unused() {
        return new UnsupportedOperationException("the autoconfiguration tests do not run this fake");
    }

    /** An expression port that throws if the core ever calls it. */
    private static final class UnusedExpressionPort implements ExpressionPort {

        @Override
        public Value evaluate(String expression, ExpressionContext context) {
            throw unused();
        }

        @Override
        public String renderTemplate(String template, ExpressionContext context) {
            throw unused();
        }
    }
}
