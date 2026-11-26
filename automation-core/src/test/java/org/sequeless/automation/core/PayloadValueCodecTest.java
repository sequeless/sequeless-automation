package org.sequeless.automation.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;

/**
 * Unit tests for the copied {@link PayloadValueCodec#fromPayload} subset. The prototype had no test of this decode
 * path of its own, so these cases cover each tag and the refusals.
 */
class PayloadValueCodecTest {

    @Test
    void decodesEachScalarTag() {
        assertThat(PayloadValueCodec.fromPayload(Map.of("text", "alice"))).isEqualTo(Value.text("alice"));
        assertThat(PayloadValueCodec.fromPayload(Map.of("integer", 42L))).isEqualTo(Value.integer(42L));
        assertThat(PayloadValueCodec.fromPayload(Map.of("bool", true))).isEqualTo(Value.bool(true));
        assertThat(PayloadValueCodec.fromPayload(Map.of("date", "2026-11-26")))
                .isEqualTo(Value.date(LocalDate.parse("2026-11-26")));
        assertThat(PayloadValueCodec.fromPayload(Map.of("dateTime", "2026-11-26T10:00:00Z")))
                .isEqualTo(Value.dateTime(Instant.parse("2026-11-26T10:00:00Z")));
    }

    @Test
    void decodesAnIntegerFromAnyNumber() {
        assertThat(PayloadValueCodec.fromPayload(Map.of("integer", 7))).isEqualTo(Value.integer(7L));
    }

    @Test
    void decodesADecimalFromAStringKeepingItsScale() {
        Value decoded = PayloadValueCodec.fromPayload(Map.of("decimal", "1.50"));

        assertThat(decoded).isEqualTo(Value.decimal(new BigDecimal("1.50")));
    }

    @Test
    void decodesADecimalFromABigDecimalUnchanged() {
        BigDecimal exact = new BigDecimal("2.000");

        assertThat(PayloadValueCodec.fromPayload(Map.of("decimal", exact))).isEqualTo(Value.decimal(exact));
    }

    @Test
    void decodesAReferenceFromItsObjectIdString() {
        ObjectId target = ObjectId.random();

        assertThat(PayloadValueCodec.fromPayload(Map.of("ref", target.value().toString())))
                .isEqualTo(Value.ref(target));
    }

    @Test
    void decodesAListOfTaggedElementsRecursively() {
        ObjectId target = ObjectId.random();
        Object payload = Map.of(
                "list",
                List.of(Map.of("text", "a"), Map.of("ref", target.value().toString())));

        assertThat(PayloadValueCodec.fromPayload(payload))
                .isEqualTo(Value.list(List.of(Value.text("a"), Value.ref(target))));
    }

    @Test
    void refusesAMapWithMoreThanOneKey() {
        Map<String, Object> payload = Map.of("text", "a", "integer", 1L);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> PayloadValueCodec.fromPayload(payload))
                .withMessageContaining("single-key tagged value map");
    }

    @Test
    void refusesAValueThatIsNotAMap() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PayloadValueCodec.fromPayload("text"))
                .withMessageContaining("single-key tagged value map");
    }

    @Test
    void refusesAnUnrecognizedTag() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PayloadValueCodec.fromPayload(Map.of("unknown", "x")))
                .withMessageContaining("Unrecognized tagged value key: unknown");
    }

    @Test
    void rejectsNullPayload() {
        assertThatNullPointerException().isThrownBy(() -> PayloadValueCodec.fromPayload(null));
    }
}
