package org.sequeless.automation.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.sequeless.automation.api.CommandProvenance;
import org.sequeless.automation.api.CreateObjectCommand;
import org.sequeless.automation.api.ObjectReadDeniedException;
import org.sequeless.automation.api.ObjectSnapshot;
import org.sequeless.automation.api.ResolvedWebhookRequest;
import org.sequeless.automation.api.SetPropertyCommand;
import org.sequeless.automation.api.StoreCommandRejectedException;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.automation.testkit.InMemoryObjectReadPort;
import org.sequeless.automation.testkit.InMemoryStoreCommandPort;
import org.sequeless.expression.api.ExpressionContext;
import org.sequeless.expression.api.ExpressionPort;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.BoolValue;
import org.sequeless.kernel.values.DateTimeValue;
import org.sequeless.kernel.values.DateValue;
import org.sequeless.kernel.values.DecimalValue;
import org.sequeless.kernel.values.IntegerValue;
import org.sequeless.kernel.values.ListValue;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ReferenceValue;
import org.sequeless.kernel.values.TextValue;
import org.sequeless.kernel.values.Value;
import org.sequeless.ontology.automation.api.CreateObjectAction;
import org.sequeless.ontology.automation.api.LogAction;
import org.sequeless.ontology.automation.api.PropertyAssignment;
import org.sequeless.ontology.automation.api.SetPropertyAction;
import org.sequeless.ontology.automation.api.WebhookAction;

/**
 * Unit tests for {@link DefaultActionExecutor}, built on the in-memory {@link InMemoryObjectReadPort} and
 * {@link InMemoryStoreCommandPort} from the testkit. The fakes record the commands the executor sends, so the tests
 * assert those commands, not Store rows.
 *
 * <p>The {@code TransitionFired} rows here are hand-built with the keys the Store writes for one. The fakes do not
 * apply the commands they record, so each test seeds the read port with the state it needs.
 */
class DefaultActionExecutorTest {

    private static final String NS = "https://sequeless.dev/ns/automation#";
    private static final String PROJECT_IRI = NS + "Project";
    private static final String TASK_IRI = NS + "Task";
    private static final String OWNER_IRI = NS + "owner";
    private static final String STATUS_IRI = NS + "status";
    private static final String TITLE_IRI = NS + "title";

    private static final String DRAFT_IRI = NS + "Draft";
    private static final String ACTIVE_IRI = NS + "Active";

    private static final Instant NOW = Instant.parse("2026-09-19T00:00:00Z");

    private static final Scope ALICE = new Scope(new TenantId("acme"), new Principal("alice", Set.of("member")));

    @Test
    void applyingEveryActionKindOnOneTransitionFiredRowAppliesThemCorrectly() {
        SetPropertyAction setPropertyValue =
                new SetPropertyAction(STATUS_IRI, Optional.of(Value.text("kicked-off")), Optional.empty());
        SetPropertyAction setPropertyExpression =
                new SetPropertyAction(STATUS_IRI, Optional.empty(), Optional.of("self.owner"));
        CreateObjectAction createObjectValue = new CreateObjectAction(
                TASK_IRI,
                List.of(new PropertyAssignment(TITLE_IRI, Optional.of(Value.text("Kickoff")), Optional.empty())));
        CreateObjectAction createObjectExpression = new CreateObjectAction(
                TASK_IRI, List.of(new PropertyAssignment(TITLE_IRI, Optional.empty(), Optional.of("self.owner"))));
        WebhookAction webhookWithBody =
                new WebhookAction("https://example.com/hook?owner=${self.owner}", "POST", Optional.of("${self.owner}"));
        WebhookAction webhookNoBody = new WebhookAction("https://example.com/reopen", "GET", Optional.empty());
        LogAction logAction = new LogAction("Activated by ${self.owner}");

        ObjectId projectId = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(projectId, project(DRAFT_IRI, Map.of(OWNER_IRI, Value.text("alice"))));
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        FakeExpressionPort expressionPort = new FakeExpressionPort();
        DefaultActionExecutor executor = executor(expressionPort, readPort, commandPort);
        StoreEventRow firedRow = transitionFiredRow(projectId, aliceSelf());

        executor.applyAction(ALICE, firedRow, setPropertyValue, 0);
        executor.applyAction(ALICE, firedRow, setPropertyExpression, 1);
        executor.applyAction(ALICE, firedRow, createObjectValue, 2);
        executor.applyAction(ALICE, firedRow, createObjectExpression, 3);
        ResolvedWebhookRequest resolved1 = executor.resolveWebhookAction(ALICE, firedRow, webhookWithBody, 4);
        ResolvedWebhookRequest resolved2 = executor.resolveWebhookAction(ALICE, firedRow, webhookNoBody, 5);
        expressionPort.renderTemplateCalls.clear();
        executor.applyAction(ALICE, firedRow, logAction, 6);

        assertThat(commandPort.setProperties())
                .containsExactly(
                        new SetPropertyCommand(
                                projectId, STATUS_IRI, Value.text("kicked-off"), provenance(firedRow, 0)),
                        new SetPropertyCommand(projectId, STATUS_IRI, Value.text("alice"), provenance(firedRow, 1)));
        assertThat(commandPort.createdObjects())
                .containsExactly(
                        new CreateObjectCommand(
                                derivedId(firedRow, 2),
                                TASK_IRI,
                                Map.of(TITLE_IRI, Value.text("Kickoff")),
                                provenance(firedRow, 2)),
                        new CreateObjectCommand(
                                derivedId(firedRow, 3),
                                TASK_IRI,
                                Map.of(TITLE_IRI, Value.text("alice")),
                                provenance(firedRow, 3)));
        assertThat(commandPort.fireRequests()).isEmpty();

        assertThat(resolved1.url()).isEqualTo("https://example.com/hook?owner=alice");
        assertThat(resolved1.method()).isEqualTo("POST");
        assertThat(resolved1.body()).contains("alice");
        assertThat(resolved2.url()).isEqualTo("https://example.com/reopen");
        assertThat(resolved2.method()).isEqualTo("GET");
        assertThat(resolved2.body()).isEmpty();

        assertThat(expressionPort.renderTemplateCalls).hasSize(1);
        assertThat(expressionPort.renderTemplateCalls.get(0).template()).isEqualTo("Activated by ${self.owner}");
        assertThat(expressionPort.renderTemplateCalls.get(0).context().selfProperties())
                .containsEntry("owner", Value.text("alice"));
    }

    // --- applyAction, SetProperty ---

    @Test
    void applyActionIsANoOpWhenValueAlreadyMatchesValueVariant() {
        ObjectId id = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(id, project(ACTIVE_IRI, Map.of(STATUS_IRI, Value.text("kicked-off"))));
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        DefaultActionExecutor executor = executor(new FakeExpressionPort(), readPort, commandPort);
        StoreEventRow row = transitionFiredRow(id, aliceSelf());
        SetPropertyAction action =
                new SetPropertyAction(STATUS_IRI, Optional.of(Value.text("kicked-off")), Optional.empty());

        executor.applyAction(ALICE, row, action, 0);

        assertThat(commandPort.setProperties()).isEmpty();
        assertThat(commandPort.createdObjects()).isEmpty();
    }

    @Test
    void applyActionIsANoOpWhenValueAlreadyMatchesExpressionVariant() {
        ObjectId id = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(id, project(ACTIVE_IRI, Map.of(STATUS_IRI, Value.text("alice"))));
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        DefaultActionExecutor executor = executor(new FakeExpressionPort(), readPort, commandPort);
        StoreEventRow row = transitionFiredRow(id, aliceSelf());
        SetPropertyAction action = new SetPropertyAction(STATUS_IRI, Optional.empty(), Optional.of("self.owner"));

        executor.applyAction(ALICE, row, action, 0);

        assertThat(commandPort.setProperties()).isEmpty();
    }

    @Test
    void applyActionSendsOneSetPropertyCommandWithProvenanceWhenValueDiffersValueVariant() {
        ObjectId id = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(id, project(ACTIVE_IRI, Map.of(STATUS_IRI, Value.text("old"))));
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        DefaultActionExecutor executor = executor(new FakeExpressionPort(), readPort, commandPort);
        StoreEventRow row = transitionFiredRow(id, aliceSelf());
        SetPropertyAction action = new SetPropertyAction(STATUS_IRI, Optional.of(Value.text("new")), Optional.empty());

        executor.applyAction(ALICE, row, action, 0);

        assertThat(commandPort.setProperties())
                .containsExactly(new SetPropertyCommand(id, STATUS_IRI, Value.text("new"), provenance(row, 0)));
        assertThat(commandPort.createdObjects()).isEmpty();
    }

    @Test
    void applyActionSetPropertyEvaluatesAnExpressionAgainstTheToStateAsTheState() {
        ObjectId id = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(id, project(DRAFT_IRI, Map.of(STATUS_IRI, Value.text("old"))));
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        DefaultActionExecutor executor = executor(new FakeExpressionPort(), readPort, commandPort);
        StoreEventRow row = transitionFiredRow(id, aliceSelf());
        SetPropertyAction action = new SetPropertyAction(STATUS_IRI, Optional.empty(), Optional.of("self.state"));

        executor.applyAction(ALICE, row, action, 0);

        // The row's fromState is DRAFT and its toState is ACTIVE; the expression sees ACTIVE.
        assertThat(commandPort.setProperties())
                .containsExactly(new SetPropertyCommand(id, STATUS_IRI, Value.text(ACTIVE_IRI), provenance(row, 0)));
    }

    @Test
    void applyActionIsASafeNoOpWhenTargetObjectNoLongerExists() {
        ObjectId id = ObjectId.random();
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        DefaultActionExecutor executor = executor(new FakeExpressionPort(), new InMemoryObjectReadPort(), commandPort);
        StoreEventRow row = transitionFiredRow(id, aliceSelf());
        SetPropertyAction action = new SetPropertyAction(STATUS_IRI, Optional.of(Value.text("new")), Optional.empty());

        executor.applyAction(ALICE, row, action, 0); // must not throw

        assertThat(commandPort.setProperties()).isEmpty();
    }

    @Test
    void applyActionSetPropertyDoesNotEvaluateItsExpressionWhenTheTargetObjectIsAbsent() {
        ObjectId id = ObjectId.random();
        FakeExpressionPort expressionPort = new FakeExpressionPort();
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        DefaultActionExecutor executor = executor(expressionPort, new InMemoryObjectReadPort(), commandPort);
        StoreEventRow row = transitionFiredRow(id, aliceSelf());
        SetPropertyAction action = new SetPropertyAction(STATUS_IRI, Optional.empty(), Optional.of("self.owner"));

        executor.applyAction(ALICE, row, action, 0);

        assertThat(expressionPort.evaluateCount).isZero();
        assertThat(commandPort.setProperties()).isEmpty();
    }

    @Test
    void applyActionRefusesANonNumericOntologyVersionStampOnASetPropertyRow() {
        ObjectId id = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(id, project(ACTIVE_IRI, Map.of(STATUS_IRI, Value.text("old"))));
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        DefaultActionExecutor executor = executor(new FakeExpressionPort(), readPort, commandPort);
        StoreEventRow row = withOntologyVersion(transitionFiredRow(id, aliceSelf()), "one");
        SetPropertyAction action = new SetPropertyAction(STATUS_IRI, Optional.of(Value.text("new")), Optional.empty());

        assertThatThrownBy(() -> executor.applyAction(ALICE, row, action, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(row.id().toString())
                .hasMessageContaining("not a number");
        assertThat(commandPort.setProperties()).isEmpty();
    }

    @Test
    void applyActionRefusesAnOntologyVersionStampOutsideTheIntRangeOnASetPropertyRow() {
        ObjectId id = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(id, project(ACTIVE_IRI, Map.of(STATUS_IRI, Value.text("old"))));
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        DefaultActionExecutor executor = executor(new FakeExpressionPort(), readPort, commandPort);
        StoreEventRow row = withOntologyVersion(transitionFiredRow(id, aliceSelf()), 4294967296L);
        SetPropertyAction action = new SetPropertyAction(STATUS_IRI, Optional.of(Value.text("new")), Optional.empty());

        assertThatThrownBy(() -> executor.applyAction(ALICE, row, action, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(row.id().toString())
                .hasMessageContaining("outside the int range");
        assertThat(commandPort.setProperties()).isEmpty();
    }

    @Test
    void applyActionRefusesARowWithoutAnOntologyVersionStampWhenASetPropertyCommandIsNeeded() {
        ObjectId id = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(id, project(ACTIVE_IRI, Map.of(STATUS_IRI, Value.text("old"))));
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        DefaultActionExecutor executor = executor(new FakeExpressionPort(), readPort, commandPort);
        StoreEventRow unstamped = withoutOntologyVersion(transitionFiredRow(id, aliceSelf()));
        SetPropertyAction action = new SetPropertyAction(STATUS_IRI, Optional.of(Value.text("new")), Optional.empty());

        assertThatThrownBy(() -> executor.applyAction(ALICE, unstamped, action, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(unstamped.id().toString())
                .hasMessageContaining("no ontologyVersion stamp");
        assertThat(commandPort.setProperties()).isEmpty();
    }

    // --- applyAction, CreateObject ---

    @Test
    void applyActionCreateObjectSupportsExpressionVariantProperties() {
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        DefaultActionExecutor executor = executor(new FakeExpressionPort(), new InMemoryObjectReadPort(), commandPort);
        StoreEventRow row = transitionFiredRow(ObjectId.random(), aliceSelf());
        CreateObjectAction action = new CreateObjectAction(
                TASK_IRI, List.of(new PropertyAssignment(TITLE_IRI, Optional.empty(), Optional.of("self.owner"))));

        executor.applyAction(ALICE, row, action, 0);

        assertThat(commandPort.createdObjects())
                .containsExactly(new CreateObjectCommand(
                        derivedId(row, 0), TASK_IRI, Map.of(TITLE_IRI, Value.text("alice")), provenance(row, 0)));
    }

    @Test
    void applyActionCreatesTheObjectAtTheDerivedIdAndCreatesNothingOnReplay() {
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        DefaultActionExecutor executor = executor(new FakeExpressionPort(), readPort, commandPort);
        StoreEventRow row = transitionFiredRow(ObjectId.random(), aliceSelf());
        CreateObjectAction action = new CreateObjectAction(
                TASK_IRI,
                List.of(new PropertyAssignment(TITLE_IRI, Optional.of(Value.text("Kickoff")), Optional.empty())));
        ObjectId expectedId = derivedId(row, 2);

        executor.applyAction(ALICE, row, action, 2);

        assertThat(commandPort.createdObjects())
                .containsExactly(new CreateObjectCommand(
                        expectedId, TASK_IRI, Map.of(TITLE_IRI, Value.text("Kickoff")), provenance(row, 2)));

        // The object now exists at the derived id, as the Store would have it after the first command.
        readPort.put(expectedId, new ObjectSnapshot(TASK_IRI, Optional.empty(), Map.of()));
        executor.applyAction(ALICE, row, action, 2);

        assertThat(commandPort.createdObjects()).hasSize(1);
    }

    @Test
    void applyActionCreateObjectDoesNotEvaluateItsPropertiesWhenTheObjectAlreadyExists() {
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        FakeExpressionPort expressionPort = new FakeExpressionPort();
        DefaultActionExecutor executor = executor(expressionPort, readPort, commandPort);
        StoreEventRow row = transitionFiredRow(ObjectId.random(), aliceSelf());
        CreateObjectAction action = new CreateObjectAction(
                TASK_IRI, List.of(new PropertyAssignment(TITLE_IRI, Optional.empty(), Optional.of("self.owner"))));
        readPort.put(derivedId(row, 0), new ObjectSnapshot(TASK_IRI, Optional.empty(), Map.of()));

        executor.applyAction(ALICE, row, action, 0);

        assertThat(expressionPort.evaluateCount).isZero();
        assertThat(commandPort.createdObjects()).isEmpty();
    }

    @Test
    void applyActionRefusesACreateObjectRowWithoutAnOntologyVersionStamp() {
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        DefaultActionExecutor executor = executor(new FakeExpressionPort(), new InMemoryObjectReadPort(), commandPort);
        StoreEventRow unstamped = withoutOntologyVersion(transitionFiredRow(ObjectId.random(), aliceSelf()));
        CreateObjectAction action = new CreateObjectAction(
                TASK_IRI,
                List.of(new PropertyAssignment(TITLE_IRI, Optional.of(Value.text("Kickoff")), Optional.empty())));

        assertThatThrownBy(() -> executor.applyAction(ALICE, unstamped, action, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(unstamped.id().toString());
        assertThat(commandPort.createdObjects()).isEmpty();
    }

    // --- applyAction, Log ---

    @Test
    void applyActionLogRendersMessageWithTheToStateAndPrincipalContextAndSendsNoCommand() {
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        FakeExpressionPort expressionPort = new FakeExpressionPort();
        DefaultActionExecutor executor = executor(expressionPort, new InMemoryObjectReadPort(), commandPort);
        StoreEventRow row = transitionFiredRow(ObjectId.random(), aliceSelf());

        executor.applyAction(ALICE, row, new LogAction("Activated by ${self.owner}"), 6);

        assertThat(expressionPort.renderTemplateCalls).hasSize(1);
        assertThat(expressionPort.renderTemplateCalls.get(0).template()).isEqualTo("Activated by ${self.owner}");
        assertThat(expressionPort.renderTemplateCalls.get(0).context().selfProperties())
                .containsEntry("owner", Value.text("alice"));
        assertThat(expressionPort.renderTemplateCalls.get(0).context().selfState())
                .contains(ACTIVE_IRI);
        assertThat(expressionPort.renderTemplateCalls.get(0).context().principalId())
                .isEqualTo("alice");
        assertThat(commandPort.setProperties()).isEmpty();
        assertThat(commandPort.createdObjects()).isEmpty();
    }

    @Test
    void applyActionLogNeedsNoOntologyVersionStamp() {
        // A log action sends no command, so it has no provenance to build and does not need the stamp.
        FakeExpressionPort expressionPort = new FakeExpressionPort();
        DefaultActionExecutor executor =
                executor(expressionPort, new InMemoryObjectReadPort(), new InMemoryStoreCommandPort());
        StoreEventRow unstamped = withoutOntologyVersion(transitionFiredRow(ObjectId.random(), aliceSelf()));

        executor.applyAction(ALICE, unstamped, new LogAction("Activated by ${self.owner}"), 6);

        assertThat(expressionPort.renderTemplateCalls).hasSize(1);
    }

    // --- applyAction, webhook refusal ---

    @Test
    void applyActionRefusesAWebhookActionAndSendsItToResolveWebhookAction() {
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        DefaultActionExecutor executor = executor(new FakeExpressionPort(), new InMemoryObjectReadPort(), commandPort);
        StoreEventRow row = transitionFiredRow(ObjectId.random(), aliceSelf());
        WebhookAction action = new WebhookAction("https://example.com/reopen", "GET", Optional.empty());

        assertThatThrownBy(() -> executor.applyAction(ALICE, row, action, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resolved through resolveWebhookAction, not applyAction");
    }

    // --- resolveWebhookAction ---

    @Test
    void resolveWebhookActionRendersUrlAndBodyAndSendsNoCommand() {
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        DefaultActionExecutor executor = executor(new FakeExpressionPort(), new InMemoryObjectReadPort(), commandPort);
        StoreEventRow row = transitionFiredRow(ObjectId.random(), aliceSelf());
        WebhookAction action =
                new WebhookAction("https://example.com/hook?owner=${self.owner}", "POST", Optional.of("${self.owner}"));

        ResolvedWebhookRequest resolved = executor.resolveWebhookAction(ALICE, row, action, 4);

        assertThat(resolved.url()).isEqualTo("https://example.com/hook?owner=alice");
        assertThat(resolved.method()).isEqualTo("POST");
        assertThat(resolved.body()).contains("alice");
        assertThat(commandPort.setProperties()).isEmpty();
        assertThat(commandPort.createdObjects()).isEmpty();
        assertThat(commandPort.fireRequests()).isEmpty();
    }

    @Test
    void resolveWebhookActionBodyIsAbsentWhenTheActionHasNoBody() {
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        DefaultActionExecutor executor = executor(new FakeExpressionPort(), new InMemoryObjectReadPort(), commandPort);
        StoreEventRow row = transitionFiredRow(ObjectId.random(), aliceSelf());
        WebhookAction action = new WebhookAction("https://example.com/reopen", "GET", Optional.empty());

        ResolvedWebhookRequest resolved = executor.resolveWebhookAction(ALICE, row, action, 5);

        assertThat(resolved.body()).isEmpty();
        assertThat(commandPort.setProperties()).isEmpty();
        assertThat(commandPort.createdObjects()).isEmpty();
    }

    // --- failures propagate and leave no effect ---

    @Test
    void applyActionLeavesNoCommandRecordedWhenTheStoreRejectsIt() {
        ObjectId id = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.put(id, project(ACTIVE_IRI, Map.of(STATUS_IRI, Value.text("old"))));
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        commandPort.rejectNext(1);
        DefaultActionExecutor executor = executor(new FakeExpressionPort(), readPort, commandPort);
        StoreEventRow row = transitionFiredRow(id, aliceSelf());
        SetPropertyAction action = new SetPropertyAction(STATUS_IRI, Optional.of(Value.text("new")), Optional.empty());

        assertThatThrownBy(() -> executor.applyAction(ALICE, row, action, 0))
                .isInstanceOf(StoreCommandRejectedException.class);
        assertThat(commandPort.setProperties()).isEmpty();
    }

    @Test
    void applyActionPropagatesADeniedReadAndSendsNoCommand() {
        ObjectId id = ObjectId.random();
        InMemoryObjectReadPort readPort = new InMemoryObjectReadPort();
        readPort.deny(id);
        InMemoryStoreCommandPort commandPort = new InMemoryStoreCommandPort();
        DefaultActionExecutor executor = executor(new FakeExpressionPort(), readPort, commandPort);
        StoreEventRow row = transitionFiredRow(id, aliceSelf());
        SetPropertyAction action = new SetPropertyAction(STATUS_IRI, Optional.of(Value.text("new")), Optional.empty());

        assertThatThrownBy(() -> executor.applyAction(ALICE, row, action, 0))
                .isInstanceOf(ObjectReadDeniedException.class);
        assertThat(commandPort.setProperties()).isEmpty();
    }

    // --- payload-building helpers ---

    private static DefaultActionExecutor executor(
            ExpressionPort expressionPort, InMemoryObjectReadPort readPort, InMemoryStoreCommandPort commandPort) {
        return new DefaultActionExecutor(expressionPort, readPort, commandPort);
    }

    private static ObjectSnapshot project(String state, Map<String, Value> properties) {
        return new ObjectSnapshot(PROJECT_IRI, Optional.of(state), properties);
    }

    /** The {@code self} map of a row whose owner is alice, tagged the way the Store writes a {@code Value}. */
    private static Map<String, Object> aliceSelf() {
        return Map.of("owner", Map.of("text", "alice"));
    }

    /** A {@code TransitionFired} row carrying the keys the Store writes on one, stamped at ontology version 1. */
    private static StoreEventRow transitionFiredRow(ObjectId objectId, Map<String, Object> self) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectId", objectId.value().toString());
        payload.put("tenantId", "acme");
        payload.put("principalId", "alice");
        payload.put("transitionName", "activate");
        payload.put("fromState", DRAFT_IRI);
        payload.put("toState", ACTIVE_IRI);
        payload.put("ontologyVersion", 1);
        payload.put("self", self);
        payload.put("typeIri", PROJECT_IRI);
        payload.put("machineIri", NS + "ProjectLifecycle");
        return new StoreEventRow(UUID.randomUUID(), StoreEventRow.KIND_TRANSITION_FIRED, payload, NOW);
    }

    private static StoreEventRow withoutOntologyVersion(StoreEventRow row) {
        Map<String, Object> payload = new HashMap<>(row.payload());
        payload.remove("ontologyVersion");
        return new StoreEventRow(row.id(), row.kind(), payload, row.occurredAt());
    }

    private static StoreEventRow withOntologyVersion(StoreEventRow row, Object stamp) {
        Map<String, Object> payload = new HashMap<>(row.payload());
        payload.put("ontologyVersion", stamp);
        return new StoreEventRow(row.id(), row.kind(), payload, row.occurredAt());
    }

    private static CommandProvenance provenance(StoreEventRow row, int actionIndex) {
        return new CommandProvenance(row.id(), actionIndex, 1);
    }

    /** The id {@link DefaultActionExecutor} derives for a CreateObject at {@code actionIndex} of {@code row}. */
    private static ObjectId derivedId(StoreEventRow row, int actionIndex) {
        return new ObjectId(UUID.nameUUIDFromBytes((row.id() + ":" + actionIndex).getBytes(StandardCharsets.UTF_8)));
    }

    // --- fakes ---

    /** Records one {@link ExpressionPort#renderTemplate} invocation for test assertions. */
    private record RenderTemplateCall(String template, ExpressionContext context) {}

    /**
     * A generic fake: {@code self.<name>} resolves {@link ExpressionContext#selfProperties()}, {@code self.state}
     * resolves {@link ExpressionContext#selfState()}, and {@code principal} resolves
     * {@link ExpressionContext#principalId()}. It mirrors real expression semantics closely enough for these tests,
     * without pulling in a JEXL adapter.
     */
    private static final class FakeExpressionPort implements ExpressionPort {

        private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]*)}");

        final List<RenderTemplateCall> renderTemplateCalls = new ArrayList<>();

        /** Counts every {@link ExpressionPort#evaluate} call, including the ones a template render makes. */
        int evaluateCount;

        @Override
        public Value evaluate(String expression, ExpressionContext context) {
            Objects.requireNonNull(expression, "expression must not be null");
            Objects.requireNonNull(context, "context must not be null");
            evaluateCount++;
            if ("self.state".equals(expression)) {
                return Value.text(context.selfState().orElseThrow());
            }
            if (expression.startsWith("self.")) {
                String name = expression.substring("self.".length());
                Value value = context.selfProperties().get(name);
                if (value == null) {
                    throw new UnsupportedOperationException("No self property named '" + name + "'");
                }
                return value;
            }
            if ("principal".equals(expression)) {
                return Value.text(context.principalId());
            }
            throw new UnsupportedOperationException("Unrecognized expression in test fixture: " + expression);
        }

        @Override
        public String renderTemplate(String template, ExpressionContext context) {
            Objects.requireNonNull(template, "template must not be null");
            Objects.requireNonNull(context, "context must not be null");
            renderTemplateCalls.add(new RenderTemplateCall(template, context));
            Matcher matcher = PLACEHOLDER.matcher(template);
            StringBuilder result = new StringBuilder();
            int last = 0;
            while (matcher.find()) {
                result.append(template, last, matcher.start());
                result.append(renderValue(evaluate(matcher.group(1), context)));
                last = matcher.end();
            }
            result.append(template.substring(last));
            return result.toString();
        }

        private static String renderValue(Value value) {
            return switch (value) {
                case TextValue text -> text.value();
                case IntegerValue integer -> Long.toString(integer.value());
                case DecimalValue decimal -> decimal.value().toString();
                case BoolValue bool -> Boolean.toString(bool.value());
                case DateTimeValue dateTime -> dateTime.value().toString();
                case DateValue date -> date.value().toString();
                case ReferenceValue ref -> ref.target().value().toString();
                case ListValue list -> list.values().toString();
            };
        }
    }
}
