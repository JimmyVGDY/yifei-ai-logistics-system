package jimmy.logistics.config;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 数据行级隔离解析器 —— 统一管理角色 → 数据过滤条件的映射。
 * <p>
 * 当前支持：
 * <ul>
 *   <li>CUSTOMER 角色 → 只查看自己关联的数据（customer_id）</li>
 * </ul>
 * <p>
 * 使用方式：Service 层调用 {@link #resolve(String)} 获取 DataScope，
 * 将其 column/value 作为参数传给 Mapper XML。
 *
 * <pre>{@code
 *   DataScope scope = dataScopeResolver.resolve("order");
 *   if (scope != null) {
 *       params.put("_dataScopeColumn", scope.column());
 *       params.put("_dataScopeValue", scope.value());
 *   }
 * }</pre>
 */
@Component
public class DataScopeResolver {

    /**
     * 数据隔离条件 —— column 为过滤列名，value 为过滤值。
     *
     * @param column 列名（如 "customer_id"）
     * @param value  列值（如客户 ID）
     */
    public record DataScope(String column, Object value) {
    }

    /**
     * 根据模块解析当前用户的数据隔离条件。
     *
     * @param module 模块标识（如 "order"、"dashboard"）
     * @return 数据隔离条件；如果当前用户不需要数据隔离返回 null
     */
    public DataScope resolve(String module) {
        String roleCode = getCurrentRoleCode();
        if ("CUSTOMER".equals(roleCode)) {
            Long customerId = getCurrentCustomerId();
            return customerId != null ? new DataScope("customer_id", customerId) : null;
        }
        return null;
    }

    private String getCurrentRoleCode() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId == null) return "";
            Object roleCode = StpUtil.getSessionByLoginId(loginId).get("roleCode");
            return roleCode != null ? String.valueOf(roleCode) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private Long getCurrentCustomerId() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId == null) return null;
            Object customerId = StpUtil.getSessionByLoginId(loginId).get("customerId");
            if (customerId instanceof Number number) {
                return number.longValue();
            }
            if (customerId != null && StringUtils.hasText(String.valueOf(customerId))) {
                return Long.valueOf(String.valueOf(customerId));
            }
        } catch (Exception e) {
            // 忽略
        }
        return null;
    }
}
