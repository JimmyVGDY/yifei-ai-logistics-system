package jimmy.ai.service;

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
        String masked = value.replaceAll("(1[3-9]\\d)\\d{4}(\\d{4})", "$1****$2");
        masked = masked.replaceAll("([\\w.+-])[\\w.+-]*@([\\w.-])[\\w.-]*(\\.[a-zA-Z]{2,})", "$1***@$2***$3");
        masked = masked.replaceAll("\\b(\\d{3})\\d{11}(\\d{4})\\b", "$1***********$2");
        masked = masked.replaceAll("(?i)(password|token|secret|authorization)\\s*[:=]\\s*\\S+", "$1=***");
        return masked;
    }
}
