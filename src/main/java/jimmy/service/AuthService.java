package jimmy.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import jimmy.model.LoginRequest;
import jimmy.model.LoginResponse;
import jimmy.model.MenuVO;
import jimmy.util.LogMaskUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;

    public AuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LoginResponse login(LoginRequest request) {
        String username = request == null ? null : request.getUsername();
        String password = request == null ? null : request.getPassword();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("登录失败，账号或密码为空");
            throw new IllegalArgumentException("账号和密码不能为空");
        }

        LoginUser loginUser = findLoginUser(username);
        if (loginUser == null || !password.equals(loginUser.password)) {
            log.warn("登录失败，账号或密码错误，username={}", LogMaskUtils.maskAccount(username));
            throw new IllegalArgumentException("账号或密码错误");
        }
        if (loginUser.status == null || loginUser.status != 1) {
            throw new IllegalArgumentException("账号已停用");
        }

        List<MenuVO> menus = mergeDefaultMenus(loginUser.roleCode, queryMenus(loginUser.roleId));
        List<String> permissions = collectPermissions(menus);

        StpUtil.login(loginUser.id);
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
        List<MenuVO> menus = getSessionList("menus");
        List<String> permissions = getSessionList("permissions");
        if (menus.isEmpty() && loginUser != null) {
            menus = mergeDefaultMenus(loginUser.roleCode, queryMenus(loginUser.roleId));
        }
        if (permissions.isEmpty()) {
            permissions = collectPermissions(menus);
        }
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
                LogMaskUtils.maskAccount(loginUser.username),
                LogMaskUtils.maskName(loginUser.realName),
                loginUser.roleCode,
                loginUser.roleName,
                permissions,
                menus
        );
    }

    private LoginUser findLoginUser(String username) {
        List<LoginUser> users = jdbcTemplate.query(
                "select u.id, u.username, u.real_name, u.password, u.status, r.id role_id, r.role_code, r.role_name " +
                        "from sys_user u left join sys_role r on r.id = u.role_id where u.username = ?",
                (rs, rowNum) -> mapUser(rs.getLong("id"), rs.getString("username"), rs.getString("real_name"),
                        rs.getString("password"), rs.getInt("status"), rs.getLong("role_id"),
                        rs.getString("role_code"), rs.getString("role_name")),
                username
        );
        return users.isEmpty() ? null : users.get(0);
    }

    private LoginUser findLoginUserById(Long userId) {
        List<LoginUser> users = jdbcTemplate.query(
                "select u.id, u.username, u.real_name, u.password, u.status, r.id role_id, r.role_code, r.role_name " +
                        "from sys_user u left join sys_role r on r.id = u.role_id where u.id = ?",
                (rs, rowNum) -> mapUser(rs.getLong("id"), rs.getString("username"), rs.getString("real_name"),
                        rs.getString("password"), rs.getInt("status"), rs.getLong("role_id"),
                        rs.getString("role_code"), rs.getString("role_name")),
                userId
        );
        return users.isEmpty() ? null : users.get(0);
    }

    private LoginUser mapUser(Long id, String username, String realName, String password, Integer status,
                              Long roleId, String roleCode, String roleName) {
        LoginUser user = new LoginUser();
        user.id = id;
        user.username = username;
        user.realName = realName;
        user.password = password;
        user.status = status;
        user.roleId = roleId;
        user.roleCode = roleCode;
        user.roleName = roleName;
        return user;
    }

    private List<MenuVO> queryMenus(Long roleId) {
        List<MenuVO> rows = jdbcTemplate.query(
                "select m.id, m.parent_id, m.menu_name, m.menu_path, m.permission_code, m.sort_no " +
                        "from sys_menu m join sys_role_menu rm on rm.menu_id = m.id " +
                        "where rm.role_id = ? and m.status = 1 order by m.sort_no, m.id",
                (rs, rowNum) -> {
                    MenuVO menu = new MenuVO();
                    menu.setId(rs.getLong("id"));
                    menu.setParentId(rs.getLong("parent_id"));
                    menu.setName(rs.getString("menu_name"));
                    menu.setPath(rs.getString("menu_path"));
                    menu.setPermissionCode(rs.getString("permission_code"));
                    menu.setSortNo(rs.getInt("sort_no"));
                    return menu;
                },
                roleId
        );
        Map<Long, MenuVO> byId = new LinkedHashMap<>();
        rows.forEach(menu -> byId.put(menu.getId(), menu));
        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO menu : rows) {
            if (menu.getParentId() != null && menu.getParentId() != 0 && byId.containsKey(menu.getParentId())) {
                byId.get(menu.getParentId()).getChildren().add(menu);
            } else {
                roots.add(menu);
            }
        }
        return roots;
    }

    private List<MenuVO> mergeDefaultMenus(String roleCode, List<MenuVO> dbMenus) {
        Map<String, MenuVO> merged = new LinkedHashMap<>();
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

    private List<String> collectPermissions(List<MenuVO> menus) {
        return flattenMenus(menus).stream()
                .map(MenuVO::getPermissionCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
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
                "drivers", "vehicles", "exceptions", "fees", "system", "users", "roles", "logs", "files", "resources");
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
                menu(-15L, -12L, "操作日志", "/system/operation-logs", "system:log:view", 930)
        ));
        menus.put("system", system);
        menus.put("users", system.getChildren().get(0));
        menus.put("roles", system.getChildren().get(1));
        menus.put("logs", system.getChildren().get(2));
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

    @SuppressWarnings("unchecked")
    private <T> List<T> getSessionList(String key) {
        Object value = StpUtil.getSession().get(key);
        return value instanceof List ? (List<T>) value : new ArrayList<>();
    }

    private static class LoginUser {
        private Long id;
        private String username;
        private String realName;
        private String password;
        private Integer status;
        private Long roleId;
        private String roleCode;
        private String roleName;
    }
}
