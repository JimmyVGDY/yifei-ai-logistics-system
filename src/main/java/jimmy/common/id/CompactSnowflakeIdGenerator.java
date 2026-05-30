package jimmy.common.id;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 紧凑型雪花 ID 生成器。
 * <p>
 * ID 格式：{@code yyMMddHHmmss + 3位序列号}（共 15 位十进制数字），
 * 每秒最多生成 1000 个 ID，适合单机部署的中小型项目。
 * </p>
 * <p>
 * 线程安全：{@code nextId()} 通过 synchronized 保证同一秒内序列号不重复，
 * 使用统一的 Instant 时间源确保秒级锁和 ID 时间戳一致。
 * </p>
 */
@Component
public class CompactSnowflakeIdGenerator {

    /** 时间格式化器：年(2位)月日时分秒，与秒级锁使用同一 Instant 时间源 */
    private static final DateTimeFormatter SECOND_FORMATTER = DateTimeFormatter.ofPattern("yyMMddHHmmss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    /** 当前秒的 epoch 秒值，synchronized 语义下确保可见性 */
    private long lastEpochSecond;

    /** 当前秒内的序列号 [0, 999]，仅在 synchronized 块内修改 */
    private int sequence;

    /**
     * 生成下一个唯一 ID。
     * <p>
     * 内部使用 epoch 秒作为时间栅栏：同一秒内递增序列号，秒切换时重置序列号。
     * ID 的字符串部分使用与锁一致的 Instant 时间源格式化，杜绝跨秒偏差。
     * </p>
     *
     * @return 15 位十进制唯一 ID
     */
    public synchronized long nextId() {
        // 统一时间源：用 Instant 获取 epoch 秒，避免 System.currentTimeMillis 与 LocalDateTime 之间的跨秒偏差
        Instant now = Instant.now();
        long currentEpochSecond = now.getEpochSecond();

        if (currentEpochSecond == lastEpochSecond) {
            // 同一秒内递增序列号
            sequence++;
            if (sequence > 999) {
                // 当前秒序列号耗尽，等待下一秒
                currentEpochSecond = waitNextSecond(currentEpochSecond);
                sequence = 0;
            }
        } else {
            // 进入新的一秒，序列号归零
            lastEpochSecond = currentEpochSecond;
            sequence = 0;
        }

        // 时间部分与秒级锁使用同一个 Instant，确保格式化结果与锁判断的秒一致
        String timePart = SECOND_FORMATTER.format(now);
        return Long.parseLong(timePart + String.format("%03d", sequence));
    }

    /**
     * 忙等待直到进入下一秒。
     * <p>
     * 此方法仅在当前秒序列号耗尽（同秒内超 1000 个请求）时调用，
     * 生产环境几乎不会触发，仅作极端并发兜底。
     * </p>
     *
     * @param currentSecond 当前已耗尽的秒
     * @return 下一秒的 epoch 秒值
     */
    private long waitNextSecond(long currentSecond) {
        long nextSecond = Instant.now().getEpochSecond();
        while (nextSecond <= currentSecond) {
            // 短暂让出 CPU，避免空转消耗
            Thread.yield();
            nextSecond = Instant.now().getEpochSecond();
        }
        lastEpochSecond = nextSecond;
        return nextSecond;
    }
}
