package jimmy.ai.service;

import jimmy.ai.model.AiToolCall;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * AI 工具输出展示校验器。
 * <p>
 * 后端审计日志可以保留必要内部上下文，但展示给前端和模型继续总结的工具摘要不能泄露
 * 权限码、SQL、表字段名和异常堆栈。
 */
@Component
public class AiToolOutputValidator {

    private static final int MAX_SUMMARY_LENGTH = 800;
    private static final Pattern PERMISSION_CODE = Pattern.compile("\\b[a-z][a-z0-9-]*(?::[a-z0-9-]+){1,3}\\b");
    private static final Pattern SQL_FRAGMENT = Pattern.compile("(?is)\\bselect\\b.+?(\\blimit\\b\\s+\\d+|$)");
    private static final Pattern STACK_TRACE = Pattern.compile("(?m)^\\s*at\\s+[\\w.$]+\\(.+\\)$");

    private final AiSensitiveDataMasker masker;

    public AiToolOutputValidator(AiSensitiveDataMasker masker) {
        this.masker = masker;
    }

    public AiToolCall sanitize(AiToolCall call) {
        if (call == null) {
            return null;
        }
        return new AiToolCall(
                safe(call.toolName()),
                safe(call.target()),
                safe(call.result())
        );
    }

    public String safe(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String safe = masker.mask(value);
        safe = SQL_FRAGMENT.matcher(safe).replaceAll("临时只读查询语句已隐藏");
        safe = STACK_TRACE.matcher(safe).replaceAll("异常堆栈已隐藏");
        safe = PERMISSION_CODE.matcher(safe).replaceAll("内部权限标识");
        if (safe.length() > MAX_SUMMARY_LENGTH) {
            return safe.substring(0, MAX_SUMMARY_LENGTH) + "...";
        }
        return safe;
    }
}
