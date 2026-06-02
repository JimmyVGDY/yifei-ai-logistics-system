package jimmy.logistics.util;

import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.mapper.LogisticsCrudMapper;
import jimmy.logistics.model.CrudFieldValue;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CRUD 业务公共工具 —— 业务编号生成、字段值组装等跨 Service 共享逻辑。
 * <p>
 * 业务编号规则：{前缀} + 6 位随机码（易读字符集：数字+大写字母排除混淆字符），
 * 碰撞后最多重试 20 次，仍碰撞回退为 Snowflake ID 作为兜底。
 */
@Component
public class CrudBusinessUtils {

    private static final String BUSINESS_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LogisticsCrudMapper logisticsCrudMapper;
    private final CompactSnowflakeIdGenerator idGenerator;

    public CrudBusinessUtils(LogisticsCrudMapper logisticsCrudMapper,
                             CompactSnowflakeIdGenerator idGenerator) {
        this.logisticsCrudMapper = logisticsCrudMapper;
        this.idGenerator = idGenerator;
    }

    /**
     * 生成不重复的业务编号（如 CUST123456、U123456）。
     * <p>
     * 先随机 6 位码尝试，碰撞后重试最多 20 次，仍碰撞则用 Snowflake ID 后 11 位兜底。
     * 字符集排除 0/O/1/I 等易混淆字符。
     */
    public String nextBusinessCode(String tableName, String columnName, String prefix) {
        for (int i = 0; i < 20; i++) {
            String code = prefix + randomCode(6);
            if (logisticsCrudMapper.countByBusinessCode(tableName, columnName, code) == 0) {
                return code;
            }
        }
        return prefix + String.valueOf(idGenerator.nextId()).substring(7);
    }

    private String randomCode(int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append(BUSINESS_CODE_CHARS.charAt(RANDOM.nextInt(BUSINESS_CODE_CHARS.length())));
        }
        return builder.toString();
    }

    /** Map → CrudFieldValue 列表，跳过 null 值 */
    public List<CrudFieldValue> toFieldValues(Map<String, Object> values) {
        List<CrudFieldValue> fields = new ArrayList<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getValue() != null) {
                fields.add(new CrudFieldValue(entry.getKey(), entry.getValue()));
            }
        }
        return fields;
    }

    /** null 安全的字符串 trim */
    public static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
