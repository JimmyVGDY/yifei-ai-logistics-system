package jimmy.common.id;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class CompactSnowflakeIdGenerator {

    private static final DateTimeFormatter SECOND_FORMATTER = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private long lastSecond;
    private int sequence;

    public synchronized long nextId() {
        long currentSecond = System.currentTimeMillis() / 1000L;
        if (currentSecond == lastSecond) {
            sequence++;
            if (sequence > 999) {
                currentSecond = waitNextSecond(currentSecond);
                sequence = 0;
            }
        } else {
            lastSecond = currentSecond;
            sequence = (int) (System.nanoTime() % 100);
        }
        String timePart = LocalDateTime.now().format(SECOND_FORMATTER);
        return Long.parseLong(timePart + String.format("%03d", sequence));
    }

    private long waitNextSecond(long currentSecond) {
        long nextSecond = System.currentTimeMillis() / 1000L;
        while (nextSecond <= currentSecond) {
            nextSecond = System.currentTimeMillis() / 1000L;
        }
        lastSecond = nextSecond;
        return nextSecond;
    }
}
