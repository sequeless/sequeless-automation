package org.sequeless.automation.inprocess;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.sequeless.automation.api.AutomationPort;
import org.sequeless.automation.api.ResolvedWebhookRequest;
import org.sequeless.automation.testkit.AutomationContract;
import org.sequeless.automation.testkit.RecordingActionExecutor;
import org.sequeless.automation.testkit.RecordingTriggerEvaluator;
import org.sequeless.ontology.automation.api.Action;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.api.TransitionRef;
import org.sequeless.ontology.schema.api.OntologyVersion;

/**
 * Proves {@link InProcessAutomationPort} satisfies every clause of {@link AutomationPort}'s behavioural
 * contract, including {@link AutomationContract}'s idempotent-reingest test. This adapter's own idempotency
 * set, not any built-in mechanism, is what makes that test pass, since the adapter has none.
 *
 * <p>Unlike {@link AutomationContract}'s default {@link RecordingActionExecutor} constructor, which points at
 * {@code http://example.invalid/hook} (deliberately unresolvable, fine for a double that never issues HTTP
 * calls), this class wires {@link #recorder} to a real local {@link HttpServer} that always answers 200. This
 * port always performs a genuine HTTP delivery inside {@code ingest}, so {@link
 * AutomationContract#ingestRoutesWebhookToResolveWebhook} would otherwise fail here, not because routing was
 * wrong, but because delivery to an unresolvable host cannot succeed.
 */
class InProcessAutomationPortContractTest extends AutomationContract {

    private static HttpServer server;
    private static String webhookUrl;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/hook", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        webhookUrl = "http://localhost:" + server.getAddress().getPort() + "/hook";
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private final RecordingActionExecutor recorder =
            new RecordingActionExecutor(new ResolvedWebhookRequest(webhookUrl, "POST", Optional.empty()));
    private final RecordingTriggerEvaluator triggerEvaluator = new RecordingTriggerEvaluator();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final InProcessTimerScheduler timerScheduler = new InProcessTimerScheduler(clock, triggerEvaluator);
    private Automation automation = new Automation(new OntologyVersion(1), Map.of());
    private final AutomationPort port = new InProcessAutomationPort(
            recorder, new InProcessAutomationProperties(), triggerEvaluator, timerScheduler, tenant -> automation);

    @Override
    protected AutomationPort port() {
        return port;
    }

    @Override
    protected void serveAutomationWithActions(Map<TransitionRef, List<Action>> actionsByTransition) {
        automation = new Automation(new OntologyVersion(1), actionsByTransition);
    }

    @Override
    protected RecordingActionExecutor recordingExecutor() {
        return recorder;
    }

    @Override
    protected RecordingTriggerEvaluator recordingTriggerEvaluator() {
        return triggerEvaluator;
    }

    @Override
    protected void advanceTime(Duration by) {
        clock.advance(by);
        timerScheduler.sweep();
    }
}
