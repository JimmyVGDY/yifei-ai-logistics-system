package jimmy.auth.config;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sa-Token 权限加载器 —— 从当前登录 Session 提取权限码列表和角色列表。
 */
@Component
public class SaPermissionConfig implements StpInterface {

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getPermissionList(Object loginId, String loginType) {
        Object permissions = StpUtil.getSessionByLoginId(loginId).get("permissions");
        return permissions instanceof List<?> permissionList ? (List<String>) permissionList : new ArrayList<>();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Object roleCode = StpUtil.getSessionByLoginId(loginId).get("roleCode");
        List<String> roles = new ArrayList<>();
        if (roleCode != null) {
            roles.add(String.valueOf(roleCode));
        }
        return roles;
    }
}
