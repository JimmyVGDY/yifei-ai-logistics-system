package jimmy.common.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import jimmy.common.util.LogMaskUtils;

/**
 * Logback 全局日志脱敏转换器。
 */
public class MaskMessageConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        if (event == null) {
            return "";
        }
        return LogMaskUtils.maskSensitiveText(event.getFormattedMessage());
    }
}
