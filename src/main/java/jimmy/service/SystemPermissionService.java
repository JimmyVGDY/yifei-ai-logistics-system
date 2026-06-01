package jimmy.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.mapper.SystemPermissionMapper;
import jimmy.model.MenuVO;
import jimmy.model.PermissionAssignmentRequest;
import jimmy.model.PermissionTreeNodeVO;
import jimmy.model.PermissionVO;
import jimmy.model.RoleMenuUpdateRequest;
import jimmy.model.UserPermissionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class SystemPermissionService {

    private static final List<String> MANAGE_ACTIONS = Arrays.asList("query", "create", "update", "delete", "import", "export");
    private static final List<String> VIEW_ACTIONS = Arrays.asList("query", "export");
    private static final Map<String, String> ACTION_NAMES = buildActionNames();

    private final SystemPermissionMapper systemPermissionMapper;
    private final CompactSnowflakeIdGenerator idGenerator;

    public SystemPermissionService(SystemPermissionMapper systemPermissionMapper,
                                   CompactSnowflakeIdGenerator idGenerator) {
        this.systemPermissionMapper = systemPermissionMapper;
        this.idGenerator = idGenerator;
    }

    /**
     * 权限基础设施采用"只补齐缺失对象"的增量方式，在应用启动时一次性执行。
     * 避免每个业务请求都执行 DDL 和权限同步，减少运行时开销。
     */
    @PostConstruct
    public void ensurePermissionInfrastructure() {
        systemPermissionMapper.createPermissionTable();
        systemPermissionMapper.createRolePermissionTable();
        systemPermissionMapper.createUserPermissionTable();
        ensureStandardMenus();
        ensurePermissionMenu();
        ensurePermissionCatalog();
        // 仅在首次启动（权限表无数据）时自动同步预设菜单和权限，后续的人工调整不再被覆盖。
        if (countAllRolePermissions() == 0) {
            ensureDefaultRoleMenus();
            syncRolePermissionsFromMenus();
        }
    }

    public List<MenuVO> menuTree() {
        return buildMenuTree(systemPermissionMapper.selectAllActiveMenus());
    }

    public List<PermissionTreeNodeVO> permissionTree() {
        List<MenuVO> menus = systemPermissionMapper.selectAllActiveMenus();
        Map<Long, PermissionTreeNodeVO> menuNodes = new LinkedHashMap<>();
        for (MenuVO menu : menus) {
            PermissionTreeNodeVO node = new PermissionTreeNodeVO();
            node.setId(menu.getId());
            node.setMenuId(menu.getId());
            node.setLabel(menu.getName());
            node.setNodeType("MENU");
            node.setPermissionCode(menu.getPermissionCode());
            menuNodes.put(menu.getId(), node);
        }

        List<PermissionVO> permissions = systemPermissionMapper.selectAllActivePermissions();
        for (PermissionVO permission : permissions) {
            PermissionTreeNodeVO permissionNode = permissionNode(permission);
            PermissionTreeNodeVO parent = permission.getMenuId() == null ? null : menuNodes.get(permission.getMenuId());
            if (parent == null) {
                menuNodes.put(permission.getId(), permissionNode);
                continue;
            }
            if (permission.getPermissionCode().equals(parent.getPermissionCode())) {
                parent.setId(permission.getId());
                parent.setPermissionId(permission.getId());
            } else {
                parent.getChildren().add(permissionNode);
            }
        }

        List<PermissionTreeNodeVO> roots = new ArrayList<>();
        for (MenuVO menu : menus) {
            PermissionTreeNodeVO node = menuNodes.get(menu.getId());
            if (menu.getParentId() != null && menu.getParentId() != 0 && menuNodes.containsKey(menu.getParentId())) {
                menuNodes.get(menu.getParentId()).getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    public List<Long> roleMenuIds(long roleId) {
        return systemPermissionMapper.selectRoleMenuIds(roleId);
    }

    public List<Long> rolePermissionIds(long roleId) {
        return systemPermissionMapper.selectRolePermissionIds(roleId);
    }

    public UserPermissionVO userPermissionIds(long userId) {
        return new UserPermissionVO(
                systemPermissionMapper.selectUserPermissionIds(userId, "GRANT"),
                systemPermissionMapper.selectUserPermissionIds(userId, "DENY")
        );
    }

    public List<String> effectivePermissionCodes(Long userId, Long roleId) {
        LinkedHashSet<String> permissions = expandPermissionCodes(safeList(systemPermissionMapper.selectRolePermissionCodes(roleId)));
        for (Map<String, Object> row : systemPermissionMapper.selectUserPermissionOverrides(userId)) {
            String code = String.valueOf(row.get("permissionCode"));
            String grantType = String.valueOf(row.get("grantType"));
            if ("DENY".equalsIgnoreCase(grantType)) {
                permissions.removeAll(expandPermissionCodes(Collections.singletonList(code)));
                continue;
            }
            permissions.addAll(expandPermissionCodes(Collections.singletonList(code)));
        }
        return new ArrayList<>(permissions);
    }

    @Transactional
    public List<Long> updateRoleMenus(long roleId, RoleMenuUpdateRequest request) {
        List<Long> menuIds = request == null || request.getMenuIds() == null ? new ArrayList<>() : request.getMenuIds();
        if (systemPermissionMapper.countRoleById(roleId) == 0) {
            throw new IllegalArgumentException("角色不存在");
        }
        systemPermissionMapper.deleteRoleMenus(roleId);
        for (Long menuId : menuIds) {
            if (menuId == null || !menuExists(menuId)) {
                continue;
            }
            systemPermissionMapper.insertRoleMenu(idGenerator.nextId(), roleId, menuId);
        }
        syncRolePermissionsFromMenus();
        log.info("角色菜单权限已更新,roleId={}, menuCount={}, operator={}", roleId, menuIds.size(), StpUtil.getLoginIdDefaultNull());
        return roleMenuIds(roleId);
    }

    @Transactional
    public List<Long> updateRolePermissions(long roleId, PermissionAssignmentRequest request) {
        if (systemPermissionMapper.countRoleById(roleId) == 0) {
            throw new IllegalArgumentException("角色不存在");
        }
        List<Long> permissionIds = normalizedIds(request == null ? null : request.getPermissionIds());
        systemPermissionMapper.deleteRolePermissions(roleId);
        for (Long permissionId : permissionIds) {
            if (permissionId == null || systemPermissionMapper.countPermissionById(permissionId) == 0) {
                continue;
            }
            systemPermissionMapper.insertRolePermission(idGenerator.nextId(), roleId, permissionId);
        }
        syncRoleMenusFromPermissions(roleId, permissionIds);
        log.info("角色细粒度权限已更新,roleId={}, permissionCount={}, operator={}", roleId, permissionIds.size(), StpUtil.getLoginIdDefaultNull());
        return rolePermissionIds(roleId);
    }

    @Transactional
    public UserPermissionVO updateUserPermissions(long userId, PermissionAssignmentRequest request) {
        if (systemPermissionMapper.countUserById(userId) == 0) {
            throw new IllegalArgumentException("用户不存在");
        }
        List<Long> grantIds = normalizedIds(request == null ? null : request.getGrantPermissionIds());
        List<Long> denyIds = normalizedIds(request == null ? null : request.getDenyPermissionIds());
        Set<Long> denySet = new LinkedHashSet<>(denyIds);
        List<Long> filteredGrantIds = new ArrayList<>();
        for (Long id : grantIds) {
            if (!denySet.contains(id)) {
                filteredGrantIds.add(id);
            }
        }
        Timestamp now = new Timestamp(System.currentTimeMillis());

        systemPermissionMapper.deleteUserPermissions(userId);
        for (Long permissionId : filteredGrantIds) {
            insertUserPermissionIfValid(userId, permissionId, "GRANT", now);
        }
        for (Long permissionId : denyIds) {
            insertUserPermissionIfValid(userId, permissionId, "DENY", now);
        }
        log.info("用户特殊权限已更新,userId={}, grantCount={}, denyCount={}, operator={}",
                userId, filteredGrantIds.size(), denyIds.size(), StpUtil.getLoginIdDefaultNull());
        return userPermissionIds(userId);
    }

    private void insertUserPermissionIfValid(Long userId, Long permissionId, String grantType, Timestamp now) {
        if (permissionId == null || systemPermissionMapper.countPermissionById(permissionId) == 0) {
            return;
        }
        systemPermissionMapper.insertUserPermission(idGenerator.nextId(), userId, permissionId, grantType, now, now);
    }

    private void syncRolePermissionsFromMenus() {
        List<PermissionVO> allPermissions = systemPermissionMapper.selectAllActivePermissions();
        // 构建 menuId → PermissionVO 的映射，只保留有 menu_id 的权限
        Map<Long, PermissionVO> permissionByMenu = new LinkedHashMap<>();
        for (PermissionVO permission : allPermissions) {
            if (permission.getMenuId() != null) {
                permissionByMenu.put(permission.getId(), permission);
            }
        }
        Map<Long, List<Long>> menuIdsByRole = roleMenuIdsByRole();
        for (Map.Entry<Long, List<Long>> entry : menuIdsByRole.entrySet()) {
            List<Long> existing = systemPermissionMapper.selectRolePermissionIds(entry.getKey());
            Set<Long> existingSet = new LinkedHashSet<>(existing);
            for (PermissionVO permission : permissionByMenu.values()) {
                if (!entry.getValue().contains(permission.getMenuId()) || existingSet.contains(permission.getId())) {
                    continue;
                }
                systemPermissionMapper.insertRolePermission(idGenerator.nextId(), entry.getKey(), permission.getId());
            }
        }
    }

    private long countAllRolePermissions() {
        long total = 0;
        List<Long> roleIds = systemPermissionMapper.selectAllRoleIds();
        for (Long roleId : roleIds) {
            List<Long> permissions = systemPermissionMapper.selectRolePermissionIds(roleId);
            if (permissions != null) {
                total += permissions.size();
            }
        }
        return total;
    }

    private void ensureStandardMenus() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Long systemMenuId = null;
        for (StandardMenu menu : standardMenus()) {
            Long parentId = menu.parentPath == null ? 0L : systemPermissionMapper.selectMenuIdByPath(menu.parentPath);
            if (menu.parentPath != null && parentId == null) {
                continue;
            }
            if (systemPermissionMapper.countMenuByPath(menu.path) == 0) {
                systemPermissionMapper.insertMenu(idGenerator.nextId(), parentId == null ? 0L : parentId,
                        menu.name, menu.path, menu.permissionCode, menu.sortNo, now, now);
            }
            if ("/system".equals(menu.path)) {
                systemMenuId = systemPermissionMapper.selectMenuIdByPath(menu.path);
            }
        }
    }

    private void ensureDefaultRoleMenus() {
        Map<String, List<String>> defaults = defaultRoleMenuPaths();
        for (Map<String, Object> role : systemPermissionMapper.selectAllRoles()) {
            Long roleId = toLong(role.get("id"));
            String roleCode = String.valueOf(role.get("roleCode"));
            if (roleId == null || systemPermissionMapper.countRoleMenus(roleId) > 0) {
                continue;
            }
            List<String> paths = defaults.getOrDefault(roleCode, defaults.get("ADMIN"));
            for (String path : paths) {
                Long menuId = systemPermissionMapper.selectMenuIdByPath(path);
                if (menuId != null) {
                    systemPermissionMapper.insertRoleMenu(idGenerator.nextId(), roleId, menuId);
                }
            }
        }
    }

    private Map<Long, List<Long>> roleMenuIdsByRole() {
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        for (Long roleId : systemPermissionMapper.selectAllRoleIds()) {
            if (systemPermissionMapper.countRoleById(roleId) > 0) {
                result.put(roleId, systemPermissionMapper.selectRoleMenuIds(roleId));
            }
        }
        return result;
    }

    private void syncRoleMenusFromPermissions(long roleId, List<Long> permissionIds) {
        List<PermissionVO> allPermissions = systemPermissionMapper.selectAllActivePermissions();
        Map<Long, PermissionVO> permissionMap = new LinkedHashMap<>();
        for (PermissionVO permission : allPermissions) {
            if (permissionIds.contains(permission.getId())) {
                permissionMap.put(permission.getId(), permission);
            }
        }
        LinkedHashSet<Long> menuIds = new LinkedHashSet<>();
        for (PermissionVO permission : permissionMap.values()) {
            Long menuId = permission.getMenuId();
            if (menuId != null && menuExists(menuId)) {
                menuIds.add(menuId);
            }
        }
        systemPermissionMapper.deleteRoleMenus(roleId);
        for (Long menuId : menuIds) {
            systemPermissionMapper.insertRoleMenu(idGenerator.nextId(), roleId, menuId);
        }
    }

    private void ensurePermissionCatalog() {
        List<MenuVO> menus = systemPermissionMapper.selectAllActiveMenus();
        for (MenuVO menu : menus) {
            if (!StringUtils.hasText(menu.getPermissionCode())) {
                continue;
            }
            PermissionVO pagePermission = buildPermission(menu, menu.getPermissionCode(), menu.getName(), "PAGE", actionFromCode(menu.getPermissionCode()), menu.getSortNo() * 100);
            insertPermissionIfMissing(pagePermission);
            String moduleCode = moduleFromCode(menu.getPermissionCode());
            for (String action : actionsFor(menu.getPermissionCode())) {
                String code = moduleCode + ":" + action;
                PermissionVO actionPermission = buildPermission(menu, code, menu.getName() + "-" + ACTION_NAMES.getOrDefault(action, action), "BUTTON", action, menu.getSortNo() * 100 + actionSort(action));
                insertPermissionIfMissing(actionPermission);
            }
        }
    }

    private PermissionVO buildPermission(MenuVO menu, String code, String name, String type, String action, int sortNo) {
        PermissionVO permission = new PermissionVO();
        permission.setId(idGenerator.nextId());
        permission.setPermissionCode(code);
        permission.setPermissionName(name);
        permission.setPermissionType(type);
        permission.setModuleCode(moduleFromCode(code));
        permission.setActionCode(action);
        permission.setMenuId(menu.getId());
        permission.setSortNo(sortNo);
        return permission;
    }

    private void insertPermissionIfMissing(PermissionVO permission) {
        if (systemPermissionMapper.countPermissionByCode(permission.getPermissionCode()) > 0) {
            return;
        }
        systemPermissionMapper.insertPermission(permission);
    }

    private PermissionTreeNodeVO permissionNode(PermissionVO permission) {
        PermissionTreeNodeVO node = new PermissionTreeNodeVO();
        node.setId(permission.getId());
        node.setPermissionId(permission.getId());
        node.setMenuId(permission.getMenuId());
        node.setLabel(permission.getPermissionName());
        node.setNodeType(permission.getPermissionType());
        node.setPermissionCode(permission.getPermissionCode());
        return node;
    }

    private boolean menuExists(Long menuId) {
        return systemPermissionMapper.countMenuById(menuId) > 0;
    }

    private List<MenuVO> buildMenuTree(List<MenuVO> rows) {
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

    private void ensurePermissionMenu() {
        Long parentId = systemPermissionMapper.selectSystemMenuId();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Long resolvedParentId = parentId == null ? 0L : parentId;
        if (systemPermissionMapper.countPermissionMenu() == 0) {
            systemPermissionMapper.insertPermissionMenu(idGenerator.nextId(), resolvedParentId, now, now);
        }
    }

    private List<StandardMenu> standardMenus() {
        List<StandardMenu> menus = new ArrayList<>();
        menus.add(new StandardMenu(null, "运营看板", "/dashboard", "dashboard:view", 10));
        menus.add(new StandardMenu(null, "运单管理", "/orders", "order:manage", 20));
        menus.add(new StandardMenu(null, "客户管理", "/customers", "customer:manage", 30));
        menus.add(new StandardMenu(null, "运单中心", "/waybills", "waybill:manage", 40));
        menus.add(new StandardMenu(null, "调度管理", "/dispatches", "dispatch:manage", 50));
        menus.add(new StandardMenu(null, "运输任务", "/tasks", "task:manage", 60));
        menus.add(new StandardMenu(null, "物流轨迹", "/tracks", "track:view", 70));
        menus.add(new StandardMenu(null, "司机管理", "/drivers", "driver:manage", 80));
        menus.add(new StandardMenu(null, "车辆管理", "/vehicles", "vehicle:manage", 90));
        menus.add(new StandardMenu(null, "异常管理", "/exceptions", "exception:manage", 100));
        menus.add(new StandardMenu(null, "费用结算", "/fees", "fee:manage", 110));
        menus.add(new StandardMenu(null, "系统管理", "/system", "system:manage", 900));
        menus.add(new StandardMenu("/system", "用户管理", "/system/users", "system:user:manage", 910));
        menus.add(new StandardMenu("/system", "角色管理", "/system/roles", "system:role:manage", 920));
        menus.add(new StandardMenu("/system", "权限配置", "/system/permissions", "system:permission:manage", 925));
        menus.add(new StandardMenu("/system", "操作日志", "/system/operation-logs", "system:log:view", 930));
        menus.add(new StandardMenu(null, "上传文件", "/files", "file:manage", 940));
        menus.add(new StandardMenu(null, "资源中心", "/resources", "resource:view", 950));
        return menus;
    }

    private Map<String, List<String>> defaultRoleMenuPaths() {
        Map<String, List<String>> defaults = new LinkedHashMap<>();
        List<String> allPaths = new ArrayList<>();
        for (StandardMenu menu : standardMenus()) {
            allPaths.add(menu.path);
        }
        defaults.put("ADMIN", allPaths);
        defaults.put("OPERATIONS_MANAGER", Arrays.asList("/dashboard", "/orders", "/waybills", "/dispatches", "/tasks", "/tracks", "/exceptions"));
        defaults.put("ORDER_OPERATOR", Arrays.asList("/orders", "/customers", "/waybills", "/tracks"));
        defaults.put("CUSTOMER_SERVICE", Arrays.asList("/customers", "/orders", "/waybills", "/tracks"));
        defaults.put("DISPATCHER", Arrays.asList("/dispatches", "/tasks", "/drivers", "/vehicles", "/tracks", "/exceptions"));
        defaults.put("FLEET_MANAGER", Arrays.asList("/drivers", "/vehicles", "/dispatches", "/tasks", "/tracks"));
        defaults.put("DRIVER", Arrays.asList("/tasks", "/tracks", "/exceptions"));
        defaults.put("EXCEPTION_HANDLER", Arrays.asList("/exceptions", "/orders", "/tasks", "/tracks"));
        defaults.put("FINANCE", Arrays.asList("/fees", "/dashboard"));
        defaults.put("FINANCE_MANAGER", Arrays.asList("/fees", "/dashboard", "/system/operation-logs"));
        defaults.put("AUDITOR", Arrays.asList("/dashboard", "/orders", "/waybills", "/tracks", "/fees", "/system/operation-logs"));
        defaults.put("FILE_MANAGER", Arrays.asList("/files", "/resources"));
        defaults.put("CUSTOMER", Arrays.asList("/orders", "/tracks"));
        return defaults;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private List<Long> normalizedIds(List<Long> ids) {
        if (ids == null) {
            return new ArrayList<>();
        }
        List<Long> result = new ArrayList<>();
        for (Long id : ids) {
            if (id != null && !result.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private LinkedHashSet<String> expandPermissionCodes(List<String> permissionCodes) {
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        for (String permissionCode : permissionCodes) {
            if (!StringUtils.hasText(permissionCode)) {
                continue;
            }
            expanded.add(permissionCode);
            String moduleCode = moduleFromCode(permissionCode);
            for (String action : actionsFor(permissionCode)) {
                expanded.add(moduleCode + ":" + action);
            }
        }
        return expanded;
    }

    private List<String> actionsFor(String permissionCode) {
        if (permissionCode.endsWith(":manage")) {
            return MANAGE_ACTIONS;
        }
        if (permissionCode.endsWith(":view")) {
            return VIEW_ACTIONS;
        }
        return Collections.singletonList(actionFromCode(permissionCode));
    }

    private String moduleFromCode(String permissionCode) {
        int index = permissionCode.lastIndexOf(':');
        return index < 0 ? permissionCode : permissionCode.substring(0, index);
    }

    private String actionFromCode(String permissionCode) {
        int index = permissionCode.lastIndexOf(':');
        return index < 0 ? permissionCode : permissionCode.substring(index + 1);
    }

    private int actionSort(String action) {
        int index = MANAGE_ACTIONS.indexOf(action);
        return index < 0 ? 99 : index + 1;
    }

    private static Map<String, String> buildActionNames() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("view", "查看页面");
        map.put("manage", "管理页面");
        map.put("query", "查询");
        map.put("create", "新增");
        map.put("update", "编辑");
        map.put("delete", "删除");
        map.put("import", "导入");
        map.put("export", "导出");
        return map;
    }

    private static class StandardMenu {
        private final String parentPath;
        private final String name;
        private final String path;
        private final String permissionCode;
        private final Integer sortNo;

        private StandardMenu(String parentPath, String name, String path, String permissionCode, Integer sortNo) {
            this.parentPath = parentPath;
            this.name = name;
            this.path = path;
            this.permissionCode = permissionCode;
            this.sortNo = sortNo;
        }
    }
}
