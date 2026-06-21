package jimmy.ai.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 生成 SQL 的第一道输出校验。
 * <p>
 * 该校验只关心模型输出形态：必须是一条 SELECT 文本。通过后仍会进入
 * {@link AiSqlSafetyValidator} 做表白名单、字段白名单、权限和敏感字段校验。
 */
@Component
public class AiSqlOutputValidator {

    private static final Pattern SELECT_PATTERN = Pattern.compile("(?is)\\bselect\\b.+");
    private static final Pattern DANGEROUS_KEYWORDS = Pattern.compile(
            "(?i)\\b(insert|update|delete|drop|alter|create|truncate|replace|call|execute|grant|revoke|load|outfile)\\b");

    private final AiOutputValidator outputValidator;

    public AiSqlOutputValidator(AiOutputValidator outputValidator) {
        this.outputValidator = outputValidator;
    }

    /**
     * 规范化模型 SQL 输出。
     *
     * @throws IllegalArgumentException 输出不符合单条 SELECT 形态时抛出
     */
    public String normalizeSelect(String rawOutput) {
        String normalized = outputValidator.stripFence(rawOutput);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("SQL_GENERATE_EMPTY：模型未生成 SQL");
        }
        normalized = extractSelect(normalized);
        normalized = normalized.replaceAll(";+$", "").strip();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("select ")) {
            throw new IllegalArgumentException("SQL_SELF_CHECK_FAILED：模型输出不是 SELECT 查询");
        }
        if (normalized.contains(";")) {
            throw new IllegalArgumentException("SQL_SECURITY_BLOCKED：禁止多语句 SQL");
        }
        if (normalized.contains("--") || normalized.contains("/*") || normalized.contains("*/")) {
            throw new IllegalArgumentException("SQL_SECURITY_BLOCKED：禁止 SQL 注释");
        }
        if (DANGEROUS_KEYWORDS.matcher(normalized).find()) {
            throw new IllegalArgumentException("SQL_SECURITY_BLOCKED：包含禁止的写操作关键字");
        }
        return normalized;
    }

    private String extractSelect(String value) {
        String trimmed = value.strip();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("select ")) {
            return trimmed;
        }
        Matcher matcher = SELECT_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group().strip();
        }
        return trimmed;
    }
}
