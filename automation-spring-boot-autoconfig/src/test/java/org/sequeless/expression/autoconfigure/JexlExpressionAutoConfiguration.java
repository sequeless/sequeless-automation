package org.sequeless.expression.autoconfigure;

import org.sequeless.expression.api.ExpressionContext;
import org.sequeless.expression.api.ExpressionPort;
import org.sequeless.kernel.values.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * A test stand-in for the expression module's {@code JexlExpressionAutoConfiguration}, which is not on this module's
 * test classpath. It carries the same name because {@code AutomationCoreAutoConfiguration} orders itself after that name
 * with {@code @AutoConfigureAfter}, and the ordering matches by name. A test that registers this class therefore runs
 * the production ordering. The {@link ExpressionPort} it supplies throws if anything calls it, since the autoconfiguration
 * tests never run the core.
 */
@AutoConfiguration
public class JexlExpressionAutoConfiguration {

    /**
     * @return an expression port that throws if the core calls it
     */
    @Bean
    public ExpressionPort expressionPort() {
        return new UnusedExpressionPort();
    }

    private static final class UnusedExpressionPort implements ExpressionPort {

        @Override
        public Value evaluate(String expression, ExpressionContext context) {
            throw unused();
        }

        @Override
        public String renderTemplate(String template, ExpressionContext context) {
            throw unused();
        }

        private static UnsupportedOperationException unused() {
            return new UnsupportedOperationException("the autoconfiguration tests do not run this fake");
        }
    }
}
