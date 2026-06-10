package jimmy.logistics.service;

import jimmy.logistics.model.OperationChangeContext;
import jimmy.common.util.FieldEncryptor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 操作变更审计服务 —— 跟踪 CRUD 操作中字段值的变化，生成可读的变更摘要。
 * <p>
 * 摘要格式示例：
 * <ul>
 *   <li>新增：新增字段：name=张***, status=启用, phone=138****5678</li>
 *   <li>更新：更新字段：status: 启用 -> 停用; name: 张*** -> 李***</li>
 *   <li>删除：删除记录：name=张***, status=启用</li>
 * </ul>
 * <p>
 * 敏感字段（密码/token）直接隐藏，不写入日志。
 * </p>
 */
@Service
public class ChangeAuditService {

    private final FieldEncryptor fieldEncryptor;

    public ChangeAuditService(FieldEncryptor fieldEncryptor) {
        this.fieldEncryptor = fieldEncryptor;
    }

    /**
     * 生成新增操作的变更摘要并写入审计上下文。
     * <p>
     * 过滤时间戳/创建人/password 等非业务字段，敏感信息脱敏后展示。
     *
     * @param values 新增字段的建表名 → 值的映射
     */
    public void recordCreate(Map<String, Object> values) {
        OperationChangeContext.setChangeSummary(createSummary(values));
    }

    /**
     * 生成编辑操作的变更摘要并写入审计上下文。
     * <p>
     * 只输出实际变化的字段，未变化的自动跳过。
     * 加密字段在比较前先解密，确保新旧值正确对比。
     *
     * @param changeValues 用户提交的变更字段
     * @param before 修改前的完整记录
     */
    public void recordUpdate(Map<String, Object> changeValues, Map<String, Object> before) {
        OperationChangeContext.setChangeSummary(updateSummary(changeValues, before));
    }

    /**
     * 生成删除操作的变更摘要并写入审计上下文。
     * 最多输出前 8 个字段值作为身份标识记录。
     */
    public void recordDelete(Map<String, Object> before) {
        OperationChangeContext.setChangeSummary(deleteSummary(before));
    }

    private String createSummary(Map<String, Object> values) {
        return "新增字段：" + safeMap(values).entrySet().stream()
                .filter(entry -> shouldAuditField(entry.getKey()))
                .map(entry -> entry.getKey() + "=" + maskValue(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private String updateSummary(Map<String, Object> changeValues, Map<String, Object> before) {
        List<String> diffs = new ArrayList<>();
        for (Map.Entry<String, Object> entry : safeMap(changeValues).entrySet()) {
            String column = entry.getKey();
            if (!shouldAuditField(column)) {
                continue;
            }
            Object beforeValue = findBeforeValue(before, column);
            Object afterValue = entry.getValue();
            if (sameValue(column, beforeValue, afterValue)) {
                continue;
            }
            diffs.add(column + ": " + maskValue(column, beforeValue) + " -> " + maskValue(column, afterValue));
        }
        return diffs.isEmpty() ? "未检测到业务字段变化" : "更新字段：" + String.join("; ", diffs);
    }

    private String deleteSummary(Map<String, Object> before) {
        if (before == null || before.isEmpty()) {
            return "删除前记录不存在或已删除";
        }
        return "删除记录：" + safeMap(before).entrySet().stream()
                .filter(entry -> shouldAuditField(entry.getKey()))
                .limit(8)
                .map(entry -> entry.getKey() + "=" + maskValue(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private Map<String, Object> safeMap(Map<String, Object> map) {
        return map == null ? new LinkedHashMap<>() : map;
    }

    private Object findBeforeValue(Map<String, Object> before, String column) {
        if (before == null) {
            return null;
        }
        if (before.containsKey(column)) {
            return before.get(column);
        }
        return before.get(toCamelCase(column));
    }

    private boolean sameValue(String column, Object beforeValue, Object afterValue) {
        return Objects.equals(normalizeAuditValue(column, beforeValue), normalizeAuditValue(column, afterValue));
    }

    private String normalizeAuditValue(String column, Object value) {
        Object normalized = value;
        if (FieldEncryptor.isEncryptedField(column) && value instanceof String cipherText) {
            normalized = fieldEncryptor.decrypt(cipherText);
        }
        return normalized == null ? "" : String.valueOf(normalized).trim();
    }

    private String maskValue(String column, Object value) {
        if (value == null) {
            return "空";
        }
        String text = normalizeAuditValue(column, value);
        if (isSecretField(column)) {
            return "已隐藏";
        }
        String lower = column.toLowerCase();
        if ("id".equals(lower) || lower.endsWith("_id") || lower.endsWith("_code") || lower.endsWith("_no")) {
            // ID、编号、单号属于审计追踪标识，保留原值便于按一次变更精确定位数据。
            return text;
        }
        if (lower.contains("phone") || lower.contains("mobile")) {
            return maskMobile(text);
        }
        if (lower.contains("email")) {
            return maskEmail(text);
        }
        if (lower.contains("name") || lower.contains("user") || lower.contains("customer") || lower.contains("driver") || lower.contains("operator")) {
            return maskName(text);
        }
        if (lower.contains("address") || lower.contains("location") || lower.contains("site")) {
            return maskLongText(text);
        }
        return text;
    }

    private boolean shouldAuditField(String column) {
        return column != null
                && !isSecretField(column)
                && !"create_by".equals(column)
                && !"update_by".equals(column)
                && !"created_at".equals(column)
                && !"updated_at".equals(column)
                && !"create_time".equals(column)
                && !"update_time".equals(column)
                && !"version".equals(column)
                && !"deleted".equals(column);
    }

    private boolean isSecretField(String column) {
        String lower = column == null ? "" : column.toLowerCase();
        return lower.contains("password") || lower.contains("token") || lower.contains("secret");
    }

    private String maskMobile(String value) {
        if (value == null || value.length() < 7) {
            return "***";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private String maskEmail(String value) {
        int atIndex = value == null ? -1 : value.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }
        return value.charAt(0) + "***" + value.substring(atIndex);
    }

    private String maskName(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() == 1) {
            return value + "*";
        }
        return value.charAt(0) + "***" + value.charAt(value.length() - 1);
    }

    private String maskLongText(String value) {
        if (value == null || value.length() <= 6) {
            return "***";
        }
        return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
    }

    private String toCamelCase(String value) {
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char c : value.toCharArray()) {
            if (c == '_') {
                upperNext = true;
                continue;
            }
            builder.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return builder.toString();
    }
}
