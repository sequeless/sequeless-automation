package org.sequeless.automation.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;

/**
 * Decodes a {@link Value} from the tagged, JSON-compatible shape a Store event payload carries: a single-key
 * {@code Map<String, Object>} whose key names the {@link Value} variant ({@code text}, {@code integer},
 * {@code decimal}, {@code bool}, {@code dateTime}, {@code date}, {@code ref} or {@code list}).
 *
 * <p>This is the {@code fromPayload} subset of the prototype's {@code org.sequeless.core.statemachine.PayloadValueCodec},
 * copied as it is. The encoder is not copied: the automation side only reads payloads, and the host encodes them.
 * The tag scheme must stay identical to the one the Store writes, so the two copies must change together.
 */
final class PayloadValueCodec {

    private static final String TAG_TEXT = "text";
    private static final String TAG_INTEGER = "integer";
    private static final String TAG_DECIMAL = "decimal";
    private static final String TAG_BOOL = "bool";
    private static final String TAG_DATE_TIME = "dateTime";
    private static final String TAG_DATE = "date";
    private static final String TAG_REF = "ref";
    private static final String TAG_LIST = "list";

    private PayloadValueCodec() {}

    /**
     * @param payload a single-key tagged map written by the Store; must not be {@code null}
     * @return the decoded {@link Value}
     * @throws NullPointerException if {@code payload} is {@code null}
     * @throws IllegalArgumentException if {@code payload} is not a single-key map with a recognized tag key
     */
    static Value fromPayload(Object payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        if (!(payload instanceof Map<?, ?> map) || map.size() != 1) {
            throw new IllegalArgumentException("Expected a single-key tagged value map, got: " + payload);
        }
        Map.Entry<?, ?> entry = map.entrySet().iterator().next();
        Object rawTag = entry.getKey();
        Object raw = entry.getValue();
        if (!(rawTag instanceof String tag)) {
            throw new IllegalArgumentException("Tagged value map key must be a String, got: " + rawTag);
        }
        return switch (tag) {
            case TAG_TEXT -> Value.text((String) raw);
            case TAG_INTEGER -> Value.integer(((Number) raw).longValue());
            case TAG_DECIMAL ->
                Value.decimal(raw instanceof BigDecimal decimal ? decimal : new BigDecimal(raw.toString()));
            case TAG_BOOL -> Value.bool((Boolean) raw);
            case TAG_DATE_TIME -> Value.dateTime(Instant.parse((String) raw));
            case TAG_DATE -> Value.date(LocalDate.parse((String) raw));
            case TAG_REF -> Value.ref(ObjectId.parse((String) raw));
            case TAG_LIST ->
                Value.list(((List<?>) raw)
                        .stream().map(PayloadValueCodec::fromPayload).toList());
            default -> throw new IllegalArgumentException("Unrecognized tagged value key: " + tag);
        };
    }
}
