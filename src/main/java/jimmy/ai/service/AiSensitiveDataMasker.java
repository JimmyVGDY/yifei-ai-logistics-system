package jimmy.ai.service;

import jimmy.common.util.LogMaskUtils;
import org.springframework.stereotype.Component;

/**
 * AI 输入输出脱敏器 —— 防止敏感信息进入模型上下文或前端展示。
 */
@Component
public class AiSensitiveDataMasker {

    public String mask(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return LogMaskUtils.maskSensitiveText(value);
    }
}
