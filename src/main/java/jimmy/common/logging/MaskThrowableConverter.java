package jimmy.common.logging;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import jimmy.common.util.LogMaskUtils;

/**
 * Logback 异常堆栈脱敏转换器。
 */
public class MaskThrowableConverter extends ThrowableProxyConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return LogMaskUtils.maskSensitiveText(super.convert(event));
    }
}
