package jimmy.ai.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 输出通用校验器。
 * <p>
 * 模型输出不能直接信任：可能带 Markdown 代码块、解释文本、超长内容或危险提示。
 * 本类只做通用文本整理，高风险 SQL 和工具摘要由专用校验器继续处理。
 */
@Component
public class AiOutputValidator {

    private static final int MAX_TEXT_LENGTH = 12000;
    private static final Pattern FENCED_BLOCK = Pattern.compile("(?is)```(?:json|sql|mysql|text)?\\s*(.*?)\\s*```");
    private static final Pattern JSON_OBJECT = Pattern.compile("(?s)\\{.*}");

    public String requireText(Optional<String> output, String stageName) {
        if (output == null || output.isEmpty() || !StringUtils.hasText(output.get())) {
            throw new IllegalArgumentException(stageName + "未返回可用内容");
        }
        return limit(stripFence(output.get()).trim());
    }

    public String stripFence(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        Matcher matcher = FENCED_BLOCK.matcher(value.trim());
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return value.trim();
    }

    public Optional<String> extractJsonObject(String value) {
        String normalized = stripFence(value);
        if (!StringUtils.hasText(normalized)) {
            return Optional.empty();
        }
        Matcher matcher = JSON_OBJECT.matcher(normalized);
        return matcher.find() ? Optional.of(limit(matcher.group())) : Optional.empty();
    }

    public String limit(String value) {
        if (value == null || value.length() <= MAX_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TEXT_LENGTH) + "\n...（AI 输出过长，已截断）";
    }
}
