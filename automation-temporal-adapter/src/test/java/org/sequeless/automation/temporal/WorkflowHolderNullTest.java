package org.sequeless.automation.temporal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.temporal.activity.ActivityOptions;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.sequeless.automation.api.ObjectChangedEvent;
import org.sequeless.automation.api.StoreEventRow;
import org.sequeless.automation.api.TimerScheduledEntry;
import org.sequeless.kernel.identity.Principal;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;

/**
 * Each workflow implementation that reads {@link TemporalActivityOptions} throws an
 * {@link IllegalStateException} naming the holder when the holder is still {@code null}. These tests
 * call each implementation directly, outside any {@code TestWorkflowEnvironment}, because the check
 * runs before any activity stub is built. A reader that is missed by the holder move would otherwise
 * fail only at workflow run time.
 *
 * <p>Each test saves the holder and restores it in a {@code finally} block, because the other test
 * classes in this package set it in their {@code @BeforeAll} methods.
 */
class WorkflowHolderNullTest {

    private static final Scope SCOPE = new Scope(new TenantId("tenant-1"), new Principal("user-1", Set.of()));
    private static final StoreEventRow ENTRY = new StoreEventRow(
            UUID.randomUUID(),
            TimerScheduledEntry.KIND,
            Map.of("objectId", UUID.randomUUID().toString()),
            Instant.parse("2026-01-01T00:00:00Z"));
    private static final ObjectChangedEvent CHANGE = new ObjectChangedEvent(
            UUID.randomUUID(),
            ObjectChangedEvent.Change.CREATED,
            Map.of("objectId", UUID.randomUUID().toString()),
            Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void changeEventWorkflowRefusesANullHolder() throws ReflectiveOperationException {
        assertRefusesANullHolder(() -> new ChangeEventWorkflowImpl().run(CHANGE, SCOPE));
    }

    @Test
    void timerWorkflowRefusesANullHolder() throws ReflectiveOperationException {
        assertRefusesANullHolder(() -> new TimerWorkflowImpl().run(ENTRY, SCOPE));
    }

    @Test
    void transitionActionWorkflowRefusesANullHolder() throws ReflectiveOperationException {
        assertRefusesANullHolder(() -> new TransitionActionWorkflowImpl().execute(ENTRY, 0));
    }

    /**
     * {@code TemporalActivityOptions.publish} refuses {@code null}, so the private holder is cleared by
     * reflection. In production the holder is null only when the autoconfiguration never ran.
     */
    private static void assertRefusesANullHolder(Runnable call) throws ReflectiveOperationException {
        Field holder = TemporalActivityOptions.class.getDeclaredField("ACTIVITY_OPTIONS");
        holder.setAccessible(true);
        ActivityOptions saved = (ActivityOptions) holder.get(null);
        holder.set(null, null);
        try {
            assertThatThrownBy(call::run)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TemporalActivityOptions.ACTIVITY_OPTIONS was never set");
        } finally {
            holder.set(null, saved);
        }
    }
}
