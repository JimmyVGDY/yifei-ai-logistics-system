package jimmy.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import jimmy.mapper.AuthMapper;
import jimmy.model.LoginRequest;
import jimmy.model.LoginResponse;
import jimmy.model.MenuVO;
import jimmy.util.LogMaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuthService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final AuthMapper authMapper;

    public AuthService(AuthMapper authMapper) {
        this.authMapper = authMapper;
    }

    public LoginResponse login(LoginRequest request) {
        String username = request == null ? null : request.getUsername();
        String password = request == null ? null : request.getPassword();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("登录失败，账号或密码为空");
            throw new IllegalArgumentException("账号和密码不能为空");
        }

        LoginUser loginUser = findLoginUser(username);
        if (loginUser == null || !matchesPassword(password, loginUser.password)) {
            log.warn("登录失败，账号或密码错误，username={}", LogMaskUtils.maskAccount(username));
            throw new IllegalArgumentException("账号或密码错误");
        }
        if (loginUser.status == null || loginUser.status != 1) {
            throw new IllegalArgumentException("账号已停用");
        }
        upgradePasswordIfNecessary(loginUser, password);

        // 菜单优先使用数据库配置，同时合并一份默认菜单兜底，避免本地开发库权限数据不完整时登录后无菜单。
        List<MenuVO> menus = mergeDefaultMenus(loginUser.roleCode, queryMenus(loginUser.roleId));
        List<String> permissions = collectPermissions(loginUser.roleCode, menus);

        // Sa-Token 会话中保存前端渲染菜单、接口鉴权和结构化日志需要的最小身份信息。
        StpUtil.login(loginUser.id);
        StpUtil.getSession().set("userCode", loginUser.userCode);
        StpUtil.getSession().set("username", loginUser.username);
        StpUtil.getSession().set("usernameMasked", LogMaskUtils.maskAccount(loginUser.username));
        StpUtil.getSession().set("realNameMasked", LogMaskUtils.maskName(loginUser.realName));
        StpUtil.getSession().set("roleCode", loginUser.roleCode);
        StpUtil.getSession().set("roleName", loginUser.roleName);
        StpUtil.getSession().set("permissions", permissions);
        StpUtil.getSession().set("menus", menus);

        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        log.info("账号登录成功，userId={}, username={}, roleCode={}",
                loginUser.id, LogMaskUtils.maskAccount(loginUser.username), loginUser.roleCode);
        return buildResponse(loginUser, tokenInfo, permissions, menus);
    }

    public LoginResponse currentSession() {
        StpUtil.checkLogin();
        Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
        LoginUser loginUser = findLoginUserById(userId);
        List<MenuVO> menus = loginUser == null ? new ArrayList<>() : mergeDefaultMenus(loginUser.roleCode, queryMenus(loginUser.roleId));
        List<String> permissions = loginUser == null ? new ArrayList<>() : collectPermissions(loginUser.roleCode, menus);
        StpUtil.getSession().set("permissions", permissions);
        StpUtil.getSession().set("menus", menus);
        return buildResponse(loginUser, StpUtil.getTokenInfo(), permissions, menus);
    }

    public void logout() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        String usernameMasked = loginId == null ? "" : String.valueOf(StpUtil.getSession().get("usernameMasked", ""));
        log.info("账号退出登录，userId={}, username={}", loginId, usernameMasked);
        StpUtil.logout();
    }

    private LoginResponse buildResponse(LoginUser loginUser, SaTokenInfo tokenInfo,
                                        List<String> permissions, List<MenuVO> menus) {
        if (loginUser == null) {
            throw new IllegalArgumentException("登录用户不存在");
        }
        return new LoginResponse(
                loginUser.username,
                loginUser.id,
                tokenInfo.getTokenName(),
                tokenInfo.getTokenValue(),
                loginUser.id,
                loginUser.userCode,
                LogMaskUtils.maskAccount(loginUser.username),
                LogMaskUtils.maskName(loginUser.realName),
                loginUser.roleCode,
                loginUser.roleName,
                permissions,
                menus
        );
    }

    private LoginUser findLoginUser(String username) {
        return mapUser(authMapper.findLoginUserByUsername(username));
    }

    private LoginUser findLoginUserById(Long userId) {
        return mapUser(authMapper.findLoginUserById(userId));
    }

    private boolean matchesPassword(String rawPassword, String storedPassword) {
        if (!StringUtils.hasText(storedPassword)) {
            return false;
        }
        // 兼容旧数据中的明文密码，登录成功后会在 upgradePasswordIfNecessary 中升级为 BCrypt。
        if (isBcrypt(storedPassword)) {
            return PASSWORD_ENCODER.matches(rawPassword, storedPassword);
        }
        return rawPassword.equals(storedPassword);
    }

    private void upgradePasswordIfNecessary(LoginUser loginUser, String rawPassword) {
        if (isBcrypt(loginUser.password)) {
            return;
        }
        String encoded = PASSWORD_ENCODER.encode(rawPassword);
        authMapper.updatePassword(loginUser.id, encoded);
        loginUser.password = encoded;
        log.info("账号密码已升级为 BCrypt，userId={}, username={}", loginUser.id, LogMaskUtils.maskAccount(loginUser.username));
    }

    private boolean isBcrypt(String password) {
        return password != null && (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$"));
    }

    private LoginUser mapUser(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        LoginUser user = new LoginUser();
        user.id = toLong(row.get("id"));
        user.userCode = toString(row.get("userCode"));
        user.username = toString(row.get("username"));
        user.realName = toString(row.get("realName"));
        user.password = toString(row.get("password"));
        user.status = toInteger(row.get("status"));
        user.roleId = toLong(row.get("roleId"));
        user.roleCode = toString(row.get("roleCode"));
        user.roleName = toString(row.get("roleName"));
        return user;
    }

    private List<MenuVO> queryMenus(Long roleId) {
        List<MenuVO> rows = authMapper.selectMenusByRoleId(roleId);
        Map<Long, MenuVO> byId = new LinkedHashMap<>();
        rows.forEach(menu -> byId.put(menu.getId(), menu));
        List<MenuVO> roots = new ArrayList<>();
        // 数据库按扁平菜单存储，这里按 parent_id 组装成前端侧边栏需要的树形结构。
        for (MenuVO menu : rows) {
            if (menu.getParentId() != null && menu.getParentId() != 0 && byId.containsKey(menu.getParentId())) {
                byId.get(menu.getParentId()).getChildren().add(menu);
            } else {
                roots.add(menu);
            }
        }
        return roots;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof BigInteger) {
            return ((BigInteger) value).longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private String toString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<MenuVO> mergeDefaultMenus(String roleCode, List<MenuVO> dbMenus) {
        Map<String, MenuVO> merged = new LinkedHashMap<>();
        // 默认菜单只作为兜底；如果数据库里配置了额外菜单，会在后面追加进来。
        for (MenuVO menu : defaultMenus(roleCode)) {
            merged.put(menu.getPath(), menu);
        }
        for (MenuVO menu : dbMenus) {
            if (StringUtils.hasText(menu.getPath()) && !merged.containsKey(menu.getPath())) {
                merged.put(menu.getPath(), menu);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<String> collectPermissions(String roleCode, List<MenuVO> menus) {
        List<String> permissions = flattenMenus(menus).stream()
                .map(MenuVO::getPermissionCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        List<String> expanded = new ArrayList<>(permissions);
        for (String permission : permissions) {
            expandActionPermissions(permission, expanded);
        }
        // 部分角色页面需要加载关联下拉数据，只补充只读查询权限，不额外开放对应菜单。
        addRelationQueryPermissions(roleCode, expanded);
        return expanded.stream().distinct().collect(Collectors.toList());
    }

    private void addRelationQueryPermissions(String roleCode, List<String> expanded) {
        if ("DISPATCHER".equals(roleCode)) {
            addQueryPermissions(expanded, "order", "waybill");
            return;
        }
        if ("DRIVER".equals(roleCode)) {
            addQueryPermissions(expanded, "order", "waybill", "dispatch", "driver", "vehicle");
            return;
        }
        if ("FINANCE".equals(roleCode)) {
            addQueryPermissions(expanded, "order");
        }
    }

    private void addQueryPermissions(List<String> expanded, String... prefixes) {
        for (String prefix : prefixes) {
            expanded.add(prefix + ":query");
        }
    }

    private void expandActionPermissions(String permission, List<String> expanded) {
        if (permission.endsWith(":manage")) {
            String prefix = permission.substring(0, permission.length() - ":manage".length());
            // manage 表示该模块完整管理权限，展开成前端按钮和后端接口都会使用的细粒度动作权限。
            expanded.add(prefix + ":query");
            expanded.add(prefix + ":create");
            expanded.add(prefix + ":update");
            expanded.add(prefix + ":delete");
            expanded.add(prefix + ":export");
            expanded.add(prefix + ":import");
            return;
        }
        if (permission.endsWith(":view")) {
            String prefix = permission.substring(0, permission.length() - ":view".length());
            expanded.add(prefix + ":query");
            expanded.add(prefix + ":export");
        }
    }

    private List<MenuVO> flattenMenus(List<MenuVO> menus) {
        List<MenuVO> flattened = new ArrayList<>();
        for (MenuVO menu : menus) {
            flattened.add(menu);
            if (menu.getChildren() != null && !menu.getChildren().isEmpty()) {
                flattened.addAll(flattenMenus(menu.getChildren()));
            }
        }
        return flattened;
    }

    private List<MenuVO> defaultMenus(String roleCode) {
        if ("CUSTOMER_SERVICE".equals(roleCode)) {
            return menus("customers", "orders", "waybills", "tracks");
        }
        if ("DISPATCHER".equals(roleCode)) {
            return menus("dispatches", "tasks", "drivers", "vehicles", "tracks", "exceptions");
        }
        if ("DRIVER".equals(roleCode)) {
            return menus("tasks", "tracks", "exceptions");
        }
        if ("FINANCE".equals(roleCode)) {
            return menus("fees", "dashboard");
        }
        if ("CUSTOMER".equals(roleCode)) {
            return menus("orders", "tracks");
        }
        return menus("dashboard", "orders", "customers", "waybills", "dispatches", "tasks", "tracks",
                "drivers", "vehicles", "exceptions", "fees", "system", "users", "roles", "permissions", "logs", "structuredLogs", "files", "resources");
    }

    private List<MenuVO> menus(String... keys) {
        Map<String, MenuVO> all = allDefaultMenus();
        List<MenuVO> result = new ArrayList<>();
        for (String key : keys) {
            MenuVO menu = all.get(key);
            if (menu != null) {
                result.add(menu);
            }
        }
        return result;
    }

    private Map<String, MenuVO> allDefaultMenus() {
        Map<String, MenuVO> menus = new LinkedHashMap<>();
        menus.put("dashboard", menu(-1L, 0L, "运营看板", "/dashboard", "dashboard:view", 10));
        menus.put("orders", menu(-2L, 0L, "运单管理", "/orders", "order:manage", 20));
        menus.put("customers", menu(-3L, 0L, "客户管理", "/customers", "customer:manage", 30));
        menus.put("waybills", menu(-4L, 0L, "运单中心", "/waybills", "waybill:manage", 40));
        menus.put("dispatches", menu(-5L, 0L, "调度管理", "/dispatches", "dispatch:manage", 50));
        menus.put("tasks", menu(-6L, 0L, "运输任务", "/tasks", "task:manage", 60));
        menus.put("tracks", menu(-7L, 0L, "物流轨迹", "/tracks", "track:view", 70));
        menus.put("drivers", menu(-8L, 0L, "司机管理", "/drivers", "driver:manage", 80));
        menus.put("vehicles", menu(-9L, 0L, "车辆管理", "/vehicles", "vehicle:manage", 90));
        menus.put("exceptions", menu(-10L, 0L, "异常管理", "/exceptions", "exception:manage", 100));
        menus.put("fees", menu(-11L, 0L, "费用结算", "/fees", "fee:manage", 110));
        MenuVO system = menu(-12L, 0L, "系统管理", "/system", "system:manage", 900);
        system.setChildren(Arrays.asList(
                menu(-13L, -12L, "用户管理", "/system/users", "system:user:manage", 910),
                menu(-14L, -12L, "角色管理", "/system/roles", "system:role:manage", 920),
                menu(-18L, -12L, "权限配置", "/system/permissions", "system:permission:manage", 925),
                menu(-15L, -12L, "操作日志", "/system/operation-logs", "system:log:view", 930),
                menu(-19L, -12L, "结构化日志", "/system/structured-logs", "system:log:view", 935)
        ));
        menus.put("system", system);
        menus.put("users", system.getChildren().get(0));
        menus.put("roles", system.getChildren().get(1));
        menus.put("permissions", system.getChildren().get(2));
        menus.put("logs", system.getChildren().get(3));
        menus.put("structuredLogs", system.getChildren().get(4));
        menus.put("files", menu(-16L, 0L, "上传文件", "/files", "file:manage", 940));
        menus.put("resources", menu(-17L, 0L, "资源中心", "/resources", "resource:view", 950));
        return menus;
    }

    private MenuVO menu(Long id, Long parentId, String name, String path, String permissionCode, Integer sortNo) {
        MenuVO menu = new MenuVO();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setName(name);
        menu.setPath(path);
        menu.setPermissionCode(permissionCode);
        menu.setSortNo(sortNo);
        return menu;
    }

    private static class LoginUser {
        private Long id;
        private String userCode;
        private String username;
        private String realName;
        private String password;
        private Integer status;
        private Long roleId;
        private String roleCode;
        private String roleName;
    }
}
