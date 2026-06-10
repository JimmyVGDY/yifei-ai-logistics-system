package jimmy.system.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.model.OperationChangeContext;
import jimmy.system.mapper.SystemPermissionMapper;
import jimmy.system.model.MenuVO;
import jimmy.system.model.PermissionAssignmentRequest;
import jimmy.system.model.PermissionTreeNodeVO;
import jimmy.system.model.PermissionVO;
import jimmy.system.model.RoleMenuUpdateRequest;
import jimmy.system.model.UserPermissionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 系统权限服务 —— RBAC 权限体系的核心实现。
 * <p>
 * 管理三大维度：菜单 → 细粒度权限 → 角色/用户的授权与禁用。
 * <p>
 * 权限计算规则：
 * <ol>
 *   <li>用户无特殊权限 → 使用角色权限（role -> role_permission）</li>
 *   <li>用户有 GRANT 权限 → 在角色权限基础上追加</li>
 *   <li>用户有 DENY 权限 → 从角色权限中移除对应模块所有 action</li>
 *   <li>管理类 (manage) 展开为 6 个 action：query/create/update/delete/import/export</li>
 *   <li>只读类 (view) 展开为 2 个 action：query/export</li>
 * </ol>
 */
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
     * 权限基础设施自检 —— 应用启动时执行一次，创建表/菜单/权限并同步初始授权。
     * <p>
     * DDL 和权限同步仅首次执行（权限表无数据时），后续人工调整不被覆盖。
     */
    @PostConstruct
    public void ensurePermissionInfrastructure() {
        systemPermissionMapper.createPermissionTable();
        systemPermissionMapper.createRolePermissionTable();
        systemPermissionMapper.createUserPermissionTable();
        ensureStandardMenus();
        ensurePermissionMenu();
        ensurePermissionCatalog();
        // 仅在首次启动（权限表无数据）时自动同步预设菜单，后续只追加新增权限，不覆盖人工调整。
        if (countAllRolePermissions() == 0) {
            ensureDefaultRoleMenus();
        }
        ensureAiMenuForAllRoles();
        syncRolePermissionsFromMenus();
    }

    /**
     * 查询完整菜单树（含子菜单），供前端侧边栏和权限配置页使用。
     */
    public List<MenuVO> menuTree() {
        return buildMenuTree(systemPermissionMapper.selectAllActiveMenus());
    }

    /**
     * 查询权限树 —— 菜单节点 + 附属的操作权限节点（BUTTON/PAGE）。
     * <p>
     * 每个菜单节点下挂载该菜单对应的细粒度按钮权限，供权限配置页 treeSelect 渲染。
     */
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

    /**
     * 查询角色已分配的菜单 ID 列表
     */
    public List<Long> roleMenuIds(long roleId) {
        return systemPermissionMapper.selectRoleMenuIds(roleId);
    }

    /**
     * 查询角色已分配的权限 ID 列表
     */
    public List<Long> rolePermissionIds(long roleId) {
        return systemPermissionMapper.selectRolePermissionIds(roleId);
    }

    /**
     * 查询用户个性化权限 ID（GRANT 和 DENY 两列）
     */
    public UserPermissionVO userPermissionIds(long userId) {
        return new UserPermissionVO(
                systemPermissionMapper.selectUserPermissionIds(userId, "GRANT"),
                systemPermissionMapper.selectUserPermissionIds(userId, "DENY")
        );
    }

    /**
     * 计算用户最终有效权限码列表。
     * <p>
     * 先取角色权限 → 展开 manage/view 为细粒度 action →
     * 应用用户级 GRANT（追加）和 DENY（移除整个模块全部 action）。
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     * @return 去重后的权限码列表
     */
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

    /**
     * 更新角色的菜单分配，同时自动同步对应权限。
     * <p>
     * 操作记录变更摘要到 {@link OperationChangeContext}。
     */
    @Transactional
    public List<Long> updateRoleMenus(long roleId, RoleMenuUpdateRequest request) {
        List<Long> menuIds = request == null || request.getMenuIds() == null ? new ArrayList<>() : request.getMenuIds();
        if (systemPermissionMapper.countRoleById(roleId) == 0) {
            throw new IllegalArgumentException("角色不存在");
        }
        List<Long> beforeMenuIds = roleMenuIds(roleId);
        systemPermissionMapper.deleteRoleMenus(roleId);
        for (Long menuId : menuIds) {
            if (menuId == null || !menuExists(menuId)) {
                continue;
            }
            systemPermissionMapper.insertRoleMenu(idGenerator.nextId(), roleId, menuId);
        }
        syncRolePermissionsFromMenus();
        OperationChangeContext.setChangeSummary("角色菜单变更，roleId=" + roleId + "，" + diffSummary(beforeMenuIds, menuIds));
        log.info("角色菜单权限已更新,roleId={}, menuCount={}, operator={}", roleId, menuIds.size(), StpUtil.getLoginIdDefaultNull());
        return roleMenuIds(roleId);
    }

    /**
     * 更新角色的细粒度权限分配，同时从权限反推菜单分配。
     * <p>
     * 操作记录变更摘要到 {@link OperationChangeContext}。
     */
    @Transactional
    public List<Long> updateRolePermissions(long roleId, PermissionAssignmentRequest request) {
        if (systemPermissionMapper.countRoleById(roleId) == 0) {
            throw new IllegalArgumentException("角色不存在");
        }
        List<Long> beforePermissionIds = rolePermissionIds(roleId);
        List<Long> permissionIds = normalizedIds(request == null ? null : request.getPermissionIds());
        systemPermissionMapper.deleteRolePermissions(roleId);
        for (Long permissionId : permissionIds) {
            if (permissionId == null || systemPermissionMapper.countPermissionById(permissionId) == 0) {
                continue;
            }
            systemPermissionMapper.insertRolePermission(idGenerator.nextId(), roleId, permissionId);
        }
        syncRoleMenusFromPermissions(roleId, permissionIds);
        OperationChangeContext.setChangeSummary("角色权限变更，roleId=" + roleId + "，" + diffSummary(beforePermissionIds, permissionIds));
        log.info("角色细粒度权限已更新,roleId={}, permissionCount={}, operator={}", roleId, permissionIds.size(), StpUtil.getLoginIdDefaultNull());
        return rolePermissionIds(roleId);
    }

    /**
     * 更新用户个性化权限（GRANT/DENY）。
     * <p>
     * 同一 ID 在 GRANT 和 DENY 中同时出现时以 DENY 为准。
     * 操作记录变更摘要到 {@link OperationChangeContext}。
     */
    @Transactional
    public UserPermissionVO updateUserPermissions(long userId, PermissionAssignmentRequest request) {
        if (systemPermissionMapper.countUserById(userId) == 0) {
            throw new IllegalArgumentException("用户不存在");
        }
        List<Long> beforeGrantIds = systemPermissionMapper.selectUserPermissionIds(userId, "GRANT");
        List<Long> beforeDenyIds = systemPermissionMapper.selectUserPermissionIds(userId, "DENY");
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
        OperationChangeContext.setChangeSummary("用户特殊权限变更，userId=" + userId
                + "，授权" + diffSummary(beforeGrantIds, filteredGrantIds)
                + "，禁用" + diffSummary(beforeDenyIds, denyIds));
        log.info("用户特殊权限已更新,userId={}, grantCount={}, denyCount={}, operator={}",
                userId, filteredGrantIds.size(), denyIds.size(), StpUtil.getLoginIdDefaultNull());
        return userPermissionIds(userId);
    }

    /**
     * 对比前后 ID 集合变化，生成本次变更的差异摘要字符串
     */
    private String diffSummary(List<Long> before, List<Long> after) {
        LinkedHashSet<Long> beforeSet = new LinkedHashSet<>(normalizedIds(before));
        LinkedHashSet<Long> afterSet = new LinkedHashSet<>(normalizedIds(after));
        List<Long> added = new ArrayList<>();
        for (Long id : afterSet) {
            if (!beforeSet.contains(id)) {
                added.add(id);
            }
        }
        List<Long> removed = new ArrayList<>();
        for (Long id : beforeSet) {
            if (!afterSet.contains(id)) {
                removed.add(id);
            }
        }
        return "新增=" + compactIds(added) + "，移除=" + compactIds(removed);
    }

    /**
     * 紧凑化 ID 列表展示，超过 20 项截断并标注总数
     */
    private String compactIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "无";
        }
        if (ids.size() <= 20) {
            return ids.toString();
        }
        return ids.subList(0, 20) + "...共" + ids.size() + "项";
    }

    private void insertUserPermissionIfValid(Long userId, Long permissionId, String grantType, Timestamp now) {
        if (permissionId == null || systemPermissionMapper.countPermissionById(permissionId) == 0) {
            return;
        }
        systemPermissionMapper.insertUserPermission(idGenerator.nextId(), userId, permissionId, grantType, now, now);
    }

    /**
     * 从所有角色的菜单自动同步细粒度权限，确保新增权限授给已分配该菜单的角色
     */
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

    /**
     * 确保标准菜单记录存在于数据库，缺失则插入
     */
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

    /**
     * 确保所有角色都有 AI 助手菜单入口。
     * <p>
     * 每次启动自动补齐：如果某角色已有 AI 菜单则跳过，未分配则新增。
     * AI 助手内部按角色权限做数据隔离（见 AiReadonlyQueryService.hasPermission），不会越权。
     */
    private void ensureAiMenuForAllRoles() {
        Long aiMenuId = systemPermissionMapper.selectMenuIdByPath("/ai-assistant");
        if (aiMenuId == null) {
            return;
        }
        for (Map<String, Object> role : systemPermissionMapper.selectAllRoles()) {
            Long roleId = toLong(role.get("id"));
            if (roleId == null) {
                continue;
            }
            List<Long> menuIds = systemPermissionMapper.selectRoleMenuIds(roleId);
            if (menuIds == null || !menuIds.contains(aiMenuId)) {
                systemPermissionMapper.insertRoleMenu(idGenerator.nextId(), roleId, aiMenuId);
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

    /**
     * 确保每个菜单的权限目录完整（PAGE + 所有 action BUTTON），缺失则插入
     */
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

    /**
     * 插入权限记录（幂等：code 已存在时跳过）
     */
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

    /**
     * 扁平菜单列表构建树形结构（按 parentId 嵌套）
     */
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
        menus.add(new StandardMenu(null, "AI助手", "/ai-assistant", "ai:chat", 960));
        return menus;
    }

    private Map<String, List<String>> defaultRoleMenuPaths() {
        Map<String, List<String>> defaults = new LinkedHashMap<>();
        List<String> allPaths = new ArrayList<>();
        for (StandardMenu menu : standardMenus()) {
            allPaths.add(menu.path);
        }
        defaults.put("ADMIN", allPaths);
        defaults.put("OPERATIONS_MANAGER", Arrays.asList("/dashboard", "/orders", "/waybills", "/dispatches", "/tasks", "/tracks", "/exceptions", "/ai-assistant"));
        defaults.put("ORDER_OPERATOR", Arrays.asList("/orders", "/customers", "/waybills", "/tracks", "/ai-assistant"));
        defaults.put("CUSTOMER_SERVICE", Arrays.asList("/customers", "/orders", "/waybills", "/tracks", "/ai-assistant"));
        defaults.put("DISPATCHER", Arrays.asList("/dispatches", "/tasks", "/drivers", "/vehicles", "/tracks", "/exceptions", "/ai-assistant"));
        defaults.put("FLEET_MANAGER", Arrays.asList("/drivers", "/vehicles", "/dispatches", "/tasks", "/tracks", "/ai-assistant"));
        defaults.put("DRIVER", Arrays.asList("/tasks", "/tracks", "/exceptions", "/ai-assistant"));
        defaults.put("EXCEPTION_HANDLER", Arrays.asList("/exceptions", "/orders", "/tasks", "/tracks", "/ai-assistant"));
        defaults.put("FINANCE", Arrays.asList("/fees", "/dashboard", "/ai-assistant"));
        defaults.put("FINANCE_MANAGER", Arrays.asList("/fees", "/dashboard", "/system/operation-logs", "/ai-assistant"));
        defaults.put("AUDITOR", Arrays.asList("/dashboard", "/orders", "/waybills", "/tracks", "/fees", "/system/operation-logs", "/ai-assistant"));
        defaults.put("FILE_MANAGER", Arrays.asList("/files", "/resources", "/ai-assistant"));
        defaults.put("CUSTOMER", Arrays.asList("/orders", "/tracks", "/ai-assistant"));
        return defaults;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
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
            permissionCode = normalizePermissionCode(permissionCode);
            expanded.add(permissionCode);
            String moduleCode = moduleFromCode(permissionCode);
            for (String action : actionsFor(permissionCode)) {
                expanded.add(moduleCode + ":" + action);
            }
        }
        return expanded;
    }

    private String normalizePermissionCode(String permissionCode) {
        if (permissionCode.startsWith("logistics:")) {
            permissionCode = permissionCode.substring("logistics:".length());
        }
        if (permissionCode.endsWith(":list")) {
            return permissionCode.substring(0, permissionCode.length() - ":list".length()) + ":query";
        }
        return permissionCode;
    }

    private List<String> actionsFor(String permissionCode) {
        if ("ai:chat".equals(permissionCode)) {
            return Arrays.asList("chat", "log:analyze", "conversation:query", "memory:query", "memory:delete", "memory:settings");
        }
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
        map.put("chat", "AI问答");
        map.put("log:analyze", "日志分析");
        map.put("conversation:query", "会话查询");
        map.putIfAbsent("memory:query", "AI长期记忆查询");
        map.putIfAbsent("memory:delete", "AI长期记忆删除");
        map.putIfAbsent("memory:settings", "AI长期记忆设置");
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
