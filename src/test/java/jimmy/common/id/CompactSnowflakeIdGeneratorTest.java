package jimmy.common.id;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompactSnowflakeIdGeneratorTest {

    @Test
    void shouldUseWorkerIdToAvoidCrossInstanceCollision() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-06-30T12:00:00Z"));
        CompactSnowflakeIdGenerator worker0 = new CompactSnowflakeIdGenerator(0, now::get);
        CompactSnowflakeIdGenerator worker1 = new CompactSnowflakeIdGenerator(1, now::get);

        Set<Long> ids = new HashSet<>();
        ids.add(worker0.nextId());
        ids.add(worker1.nextId());

        assertThat(ids).hasSize(2);
    }

    @Test
    void shouldRejectClockRollback() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-06-30T12:00:01Z"));
        CompactSnowflakeIdGenerator generator = new CompactSnowflakeIdGenerator(0, now::get);
        generator.nextId();
        now.set(Instant.parse("2026-06-30T12:00:00Z"));

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("时钟发生回拨");
    }
}
