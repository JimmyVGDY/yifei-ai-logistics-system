package jimmy.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import jimmy.mapper.AuthMapper;
import jimmy.model.LoginConflictResponse;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AuthService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final AuthMapper authMapper;
    private final SystemPermissionService systemPermissionService;
    private final LoginConflictService loginConflictService;

    public AuthService(AuthMapper authMapper, SystemPermissionService systemPermissionService,
                       LoginConflictService loginConflictService) {
        this.authMapper = authMapper;
        this.systemPermissionService = systemPermissionService;
        this.loginConflictService = loginConflictService;
    }

    public Object login(LoginRequest request) {
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
        if (StpUtil.isLogin(loginUser.id)) {
            log.info("检测到同一账号已有在线会话，创建登录冲突确认，userId={}, username={}",
                    loginUser.id, LogMaskUtils.maskAccount(loginUser.username));
            return loginConflictService.create(loginUser.id, loginUser.username);
        }
        return completeLogin(loginUser);
    }

    private LoginResponse completeLogin(LoginUser loginUser) {
        List<String> permissions = systemPermissionService.effectivePermissionCodes(loginUser.id, loginUser.roleId);
        List<MenuVO> menus = queryMenus(loginUser.roleId, permissions);
        if (menus.isEmpty()) {
            menus = defaultMenus(loginUser.roleCode);
            permissions = collectPermissions(loginUser.roleCode, menus);
        } else {
            addRelationQueryPermissions(loginUser.roleCode, permissions);
            permissions = distinctList(permissions);
        }

        // Sa-Token 会话中保存前端渲染菜单、接口鉴权和操作日志需要的最小身份信息。
        // 使用 getSessionByLoginId 可确保与 SaPermissionConfig 读取端一致，同时兼容 H2 内存模式。
        // 登录时强制单账号单会话：新 token 生效后，旧 token 会被 Sa-Token 自动踢下线。
        // 这里显式声明策略，避免环境变量误改全局配置后出现同一账号多处同时在线。
        StpUtil.login(loginUser.id, SaLoginModel.create().setIsConcurrent(false).setIsShare(false));
        StpUtil.getSessionByLoginId(loginUser.id).set("userCode", loginUser.userCode);
        StpUtil.getSessionByLoginId(loginUser.id).set("username", loginUser.username);
        StpUtil.getSessionByLoginId(loginUser.id).set("usernameMasked", LogMaskUtils.maskAccount(loginUser.username));
        StpUtil.getSessionByLoginId(loginUser.id).set("realNameMasked", LogMaskUtils.maskName(loginUser.realName));
        StpUtil.getSessionByLoginId(loginUser.id).set("roleCode", loginUser.roleCode);
        StpUtil.getSessionByLoginId(loginUser.id).set("roleName", loginUser.roleName);
        StpUtil.getSessionByLoginId(loginUser.id).set("permissions", permissions);
        StpUtil.getSessionByLoginId(loginUser.id).set("menus", menus);

        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        log.info("账号登录成功，userId={}, username={}, roleCode={}",
                loginUser.id, LogMaskUtils.maskAccount(loginUser.username), loginUser.roleCode);
        return buildResponse(loginUser, tokenInfo, permissions, menus);
    }

    public LoginResponse currentSession() {
        StpUtil.checkLogin();
        Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
        LoginUser loginUser = findLoginUserById(userId);
        // 用户被删除后 Token 可能依然有效，提前抛业务异常避免后续 NPE。
        if (loginUser == null) {
            throw new IllegalArgumentException("登录用户不存在，请重新登录");
        }
        List<String> permissions = systemPermissionService.effectivePermissionCodes(userId, loginUser.roleId);
        List<MenuVO> menus = queryMenus(loginUser.roleId, permissions);
        if (menus.isEmpty()) {
            menus = defaultMenus(loginUser.roleCode);
            permissions = collectPermissions(loginUser.roleCode, menus);
        } else {
            addRelationQueryPermissions(loginUser.roleCode, permissions);
            permissions = distinctList(permissions);
        }
        // 与会话初始化保持一致的写入策略，确保 H2 等环境下读写同一会话域。
        StpUtil.getSessionByLoginId(userId).set("permissions", permissions);
        StpUtil.getSessionByLoginId(userId).set("menus", menus);
        return buildResponse(loginUser, StpUtil.getTokenInfo(), permissions, menus);
    }

    public LoginConflictResponse loginConflictStatus(String conflictId) {
        LoginConflictService.PendingLoginConflict conflict = loginConflictService.pending(conflictId);
        if (conflict == null) {
            return loginConflictService.status(conflictId);
        }
        // 原会话已接受时跳过超时等待，立即为新页面完成登录
        boolean accepted = "ACCEPTED".equals(conflict.getStatus());
        if (!accepted && !loginConflictService.isExpired(conflict)) {
            return loginConflictService.status(conflictId);
        }
        LoginUser loginUser = findLoginUserById(conflict.getUserId());
        if (loginUser == null || loginUser.status == null || loginUser.status != 1) {
            LoginConflictResponse response = loginConflictService.status(conflictId);
            response.setLoginStatus("REJECTED");
            response.setMessage("登录账号不存在或已停用");
            return response;
        }
        LoginResponse loginResponse = completeLogin(loginUser);
        loginConflictService.markTakenOver(conflictId);
        LoginConflictResponse response = loginConflictService.status(conflictId);
        response.setLoginStatus("TAKEN_OVER");
        response.setLoginResponse(loginResponse);
        return response;
    }

    public LoginConflictResponse currentLoginConflict() {
        StpUtil.checkLogin();
        Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
        return loginConflictService.current(userId);
    }

    public LoginConflictResponse rejectLoginConflict(String conflictId) {
        StpUtil.checkLogin();
        Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
        return loginConflictService.reject(conflictId, userId);
    }

    /** 原会话主动接受新的登录请求，只标记状态，由新页面轮询完成登录 */
    public LoginConflictResponse acceptLoginConflict(String conflictId) {
        StpUtil.checkLogin();
        Long userId = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
        LoginConflictService.PendingLoginConflict conflict = loginConflictService.pending(conflictId);
        if (conflict == null || !userId.equals(conflict.getUserId())) {
            LoginConflictResponse response = loginConflictService.status(conflictId);
            response.setLoginStatus("REJECTED");
            response.setMessage("登录确认请求不存在或无权处理");
            return response;
        }
        // 仅标记为已接受，不在此处进行 completeLogin（避免影响当前会话的 token）。
        // 新页面轮询 loginConflictStatus 检测到 ACCEPTED 后会自行完成登录。
        loginConflictService.accept(conflictId);
        LoginConflictResponse response = loginConflictService.status(conflictId);
        response.setLoginStatus("ACCEPTED");
        response.setMessage("已允许新的登录请求");
        return response;
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

    private List<MenuVO> queryMenus(Long roleId, List<String> permissions) {
        Map<Long, MenuVO> mergedRows = new LinkedHashMap<>();
        for (MenuVO menu : authMapper.selectMenusByRoleId(roleId)) {
            if (canShowMenu(menu, permissions)) {
                mergedRows.put(menu.getId(), menu);
            }
        }
        for (MenuVO menu : authMapper.selectMenusByPermissionCodes(menuLookupPermissionCodes(permissions))) {
            if (canShowMenu(menu, permissions)) {
                mergedRows.put(menu.getId(), menu);
            }
        }
        List<MenuVO> rows = new ArrayList<>(mergedRows.values());
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

    private List<String> menuLookupPermissionCodes(List<String> permissions) {
        LinkedHashSet<String> codes = new LinkedHashSet<>(permissions);
        for (String permission : permissions) {
            String module = moduleFromPermission(permission);
            codes.add(module + ":manage");
            codes.add(module + ":view");
        }
        return new ArrayList<>(codes);
    }

    private boolean canShowMenu(MenuVO menu, List<String> permissions) {
        if (menu == null || !StringUtils.hasText(menu.getPermissionCode())) {
            return true;
        }
        if (permissions.contains(menu.getPermissionCode())) {
            return true;
        }
        String module = moduleFromPermission(menu.getPermissionCode());
        String prefix = module + ":";
        for (String permission : permissions) {
            if (permission.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 列表去重，保持顺序 */
    private List<String> distinctList(List<String> list) {
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (!result.contains(s)) {
                result.add(s);
            }
        }
        return result;
    }

    private String moduleFromPermission(String permissionCode) {
        int index = permissionCode.lastIndexOf(':');
        return index < 0 ? permissionCode : permissionCode.substring(0, index);
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
        List<String> permissions = new ArrayList<>();
        for (MenuVO menu : flattenMenus(menus)) {
            if (StringUtils.hasText(menu.getPermissionCode()) && !permissions.contains(menu.getPermissionCode())) {
                permissions.add(menu.getPermissionCode());
            }
        }
        List<String> expanded = new ArrayList<>(permissions);
        for (String permission : permissions) {
            expandActionPermissions(permission, expanded);
        }
        // 部分角色页面需要加载关联下拉数据，只补充只读查询权限，不额外开放对应菜单。
        addRelationQueryPermissions(roleCode, expanded);
        return distinctList(expanded);
    }

    private void addRelationQueryPermissions(String roleCode, List<String> expanded) {
        if ("OPERATIONS_MANAGER".equals(roleCode)) {
            addQueryPermissions(expanded, "customer", "driver", "vehicle", "fee");
            return;
        }
        if ("ORDER_OPERATOR".equals(roleCode) || "CUSTOMER_SERVICE".equals(roleCode)) {
            addQueryPermissions(expanded, "customer", "order", "waybill", "track");
            return;
        }
        if ("DISPATCHER".equals(roleCode)) {
            addQueryPermissions(expanded, "order", "waybill");
            return;
        }
        if ("FLEET_MANAGER".equals(roleCode)) {
            addQueryPermissions(expanded, "order", "waybill", "dispatch", "driver", "vehicle");
            return;
        }
        if ("DRIVER".equals(roleCode)) {
            addQueryPermissions(expanded, "order", "waybill", "dispatch", "driver", "vehicle");
            return;
        }
        if ("EXCEPTION_HANDLER".equals(roleCode)) {
            addQueryPermissions(expanded, "order", "task", "track");
            return;
        }
        if ("FINANCE".equals(roleCode)) {
            addQueryPermissions(expanded, "order");
            return;
        }
        if ("FINANCE_MANAGER".equals(roleCode)) {
            addQueryPermissions(expanded, "order", "fee");
            return;
        }
        if ("AUDITOR".equals(roleCode)) {
            addQueryPermissions(expanded, "order", "waybill", "track", "fee", "system:log");
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
        if ("ORDER_OPERATOR".equals(roleCode)) {
            return menus("orders", "customers", "waybills", "tracks");
        }
        if ("DISPATCHER".equals(roleCode)) {
            return menus("dispatches", "tasks", "drivers", "vehicles", "tracks", "exceptions");
        }
        if ("FLEET_MANAGER".equals(roleCode)) {
            return menus("drivers", "vehicles", "dispatches", "tasks", "tracks");
        }
        if ("DRIVER".equals(roleCode)) {
            return menus("tasks", "tracks", "exceptions");
        }
        if ("EXCEPTION_HANDLER".equals(roleCode)) {
            return menus("exceptions", "orders", "tasks", "tracks");
        }
        if ("FINANCE".equals(roleCode)) {
            return menus("fees", "dashboard");
        }
        if ("FINANCE_MANAGER".equals(roleCode)) {
            return menus("fees", "dashboard", "system", "operationLogs");
        }
        if ("OPERATIONS_MANAGER".equals(roleCode)) {
            return menus("dashboard", "orders", "waybills", "dispatches", "tasks", "tracks", "exceptions");
        }
        if ("AUDITOR".equals(roleCode)) {
            return menus("dashboard", "orders", "waybills", "tracks", "fees", "system", "operationLogs");
        }
        if ("FILE_MANAGER".equals(roleCode)) {
            return menus("files", "resources");
        }
        if ("CUSTOMER".equals(roleCode)) {
            return menus("orders", "tracks");
        }
        return menus("dashboard", "orders", "customers", "waybills", "dispatches", "tasks", "tracks",
                "drivers", "vehicles", "exceptions", "fees", "system", "users", "roles", "permissions", "files", "resources");
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
                menu(-15L, -12L, "操作日志", "/system/operation-logs", "system:log:view", 930)
        ));
        menus.put("system", system);
        menus.put("users", system.getChildren().get(0));
        menus.put("roles", system.getChildren().get(1));
        menus.put("permissions", system.getChildren().get(2));
        menus.put("operationLogs", system.getChildren().get(3));
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
