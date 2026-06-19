package jimmy.system.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.logistics.model.OperationChangeContext;
import jimmy.system.config.StandardColumnRegistry;
import jimmy.system.mapper.SystemPermissionMapper;
import jimmy.system.model.MenuVO;
import jimmy.system.model.PermissionAssignmentRequest;
import jimmy.system.model.PermissionTreeNodeVO;
import jimmy.system.model.PermissionVO;
import jimmy.system.model.RoleMenuUpdateRequest;
import jimmy.system.model.UserPermissionVO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
 *   <li>用户有 DENY 权限 → 前缀匹配移除：DENY "order" → 移除全部 order:*，DENY "order:column:total_amount" → 仅移除该列</li>
 *   <li>管理类 (manage) 展开为 6 个 action + 全部列权限</li>
 *   <li>只读类 (view) 展开为 2 个 action + 非敏感列权限</li>
 * </ol>
 */
@Slf4j
@Service
public class SystemPermissionService {

    private static final List<String> MANAGE_ACTIONS = Arrays.asList("query", "create", "update", "delete", "import", "export");
    private static final List<String> VIEW_ACTIONS = Arrays.asList("query", "export");
    private static final String COLUMN_PERMISSION_MARKER = ":column:";
    private static final Map<String, String> ACTION_NAMES = buildActionNames();

    private final SystemPermissionMapper systemPermissionMapper;
    private final CompactSnowflakeIdGenerator idGenerator;
    private final StandardColumnRegistry columnRegistry;

    public SystemPermissionService(SystemPermissionMapper systemPermissionMapper,
                                   CompactSnowflakeIdGenerator idGenerator,
                                   StandardColumnRegistry columnRegistry) {
        this.systemPermissionMapper = systemPermissionMapper;
        this.idGenerator = idGenerator;
        this.columnRegistry = columnRegistry;
    }

    /**
     * 权限基础设施自检 —— 应用启动时执行一次，创建表/菜单/权限并同步初始授权。
     * <p>
     * DDL（CREATE TABLE IF NOT EXISTS）仅用于开发/H2 环境的快速搭建，生产环境建议使用迁移脚本。
     * 通过 {@code app.permission.auto-ddl} 控制是否自动建表，默认开启。
     */
    @Value("${app.permission.auto-ddl:true}")
    private boolean autoDdl;

    @PostConstruct
    public void ensurePermissionInfrastructure() {
        if (autoDdl) {
            log.info("自动建表已启用（app.permission.auto-ddl=true），生产环境建议关闭并使用迁移脚本");
            systemPermissionMapper.createPermissionTable();
            try {
                systemPermissionMapper.addSensitiveFlagColumn();
            } catch (Exception e) {
                // 列已存在时忽略（MySQL 不支持 ADD COLUMN IF NOT EXISTS）
                log.debug("sensitive_flag 列可能已存在，跳过: {}", e.getMessage());
            }
            systemPermissionMapper.createRolePermissionTable();
            systemPermissionMapper.createUserPermissionTable();
        }
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
     * 查询权限树 —— 菜单节点 + 附属的操作权限节点（BUTTON/PAGE） + 列权限（COLUMN_GROUP 包裹）。
     * <p>
     * 每个菜单节点下按顺序挂载：PAGE → BUTTON → COLUMN_GROUP（含所有 COLUMN 子节点）。
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
            if ("COLUMN".equals(permission.getPermissionType())) {
                // 列权限统一挂到对应菜单下的 COLUMN_GROUP 节点
                PermissionTreeNodeVO menuNode = menuNodes.get(permission.getMenuId());
                if (menuNode == null) continue;
                PermissionTreeNodeVO groupNode = getOrCreateColumnGroup(menuNode);
                groupNode.getChildren().add(permissionNode(permission));
                continue;
            }
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
     * 在菜单节点下查找或创建 COLUMN_GROUP 节点。
     * <p>
     * COLUMN_GROUP 使用字符串 ID（col_group_{menuId}）确保不会与数据库 bigint ID 碰撞。
     */
    private PermissionTreeNodeVO getOrCreateColumnGroup(PermissionTreeNodeVO menuNode) {
        for (PermissionTreeNodeVO child : menuNode.getChildren()) {
            if ("COLUMN_GROUP".equals(child.getNodeType())) {
                return child;
            }
        }
        PermissionTreeNodeVO group = new PermissionTreeNodeVO();
        // 负值 ID 确保 COLUMN_GROUP 与数据库 bigint ID 不冲突，且 el-tree 的 node-key 唯一
        group.setId(-menuNode.getMenuId());
        group.setLabel("可见列");
        group.setNodeType("COLUMN_GROUP");
        group.setPermissionCode(null);
        group.setChildren(new ArrayList<>());
        menuNode.getChildren().add(group);
        return group;
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
     * 先取角色权限 → 展开 manage/view 为细粒度 action + 列权限 →
     * 应用用户级 GRANT（追加）和 DENY（前缀匹配移除）。
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
                // 前缀匹配移除：
                //   DENY "order:manage" → 移除所有 order:* (包括展开的 action 和 column)
                //   DENY "order:column:total_amount" → 仅移除该精准列权限
                String denyModulePrefix = extractDenyModulePrefix(code);
                List<String> toRemove = new ArrayList<>();
                for (String perm : permissions) {
                    if (perm.equals(code) || perm.startsWith(denyModulePrefix)) {
                        toRemove.add(perm);
                    }
                }
                permissions.removeAll(toRemove);
                permissions.remove(code);
                continue;
            }
            permissions.addAll(expandPermissionCodes(Collections.singletonList(code)));
        }
        return new ArrayList<>(permissions);
    }

    /**
     * 计算结构化权限 —— 供登录/会话返回给前端。
     * <p>
     * 以 {@link StandardColumnRegistry#moduleNames()} 区分模块化权限 vs 独立权限：
     * <ul>
     *   <li>模块在 registry 中 → 归入其 actions 和 columns</li>
     *   <li>其余权限码 → 归入 _standalone 数组</li>
     * </ul>
     *
     * @param userId 用户 ID
     * @param roleId 角色 ID
     * @return module → ModulePermissions 的映射，含 "_standalone" 键
     */
    public Map<String, Map<String, Object>> structuredPermissions(Long userId, Long roleId) {
        List<String> codes = effectivePermissionCodes(userId, roleId);
        Set<String> columnModules = columnRegistry.moduleNames();

        Map<String, List<String>> moduleActions = new LinkedHashMap<>();
        Map<String, List<String>> moduleColumns = new LinkedHashMap<>();
        List<String> standalone = new ArrayList<>();

        for (String code : codes) {
            String module = moduleFromCode(code);
            if (columnModules.contains(module)) {
                String action = actionFromCode(code);
                if (action.startsWith("column:")) {
                    moduleColumns.computeIfAbsent(module, k -> new ArrayList<>())
                            .add(action.substring("column:".length()));
                } else {
                    moduleActions.computeIfAbsent(module, k -> new ArrayList<>())
                            .add(action);
                }
            } else {
                standalone.add(code);
            }
        }

        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String module : columnModules) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("actions", moduleActions.getOrDefault(module, List.of()));
            entry.put("columns", moduleColumns.getOrDefault(module, List.of()));
            result.put(module, entry);
        }
        // 仪表盘等无标准列的模块也需占位
        for (String module : moduleActions.keySet()) {
            if (!result.containsKey(module)) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("actions", moduleActions.getOrDefault(module, List.of()));
                entry.put("columns", List.of());
                result.put(module, entry);
            }
        }
        // _standalone 始终存在
        Map<String, Object> standaloneEntry = new LinkedHashMap<>();
        standaloneEntry.put("actions", List.of());
        standaloneEntry.put("columns", List.of());
        result.put("_standalone", standaloneEntry);
        // 用 "actions" 字段存独立权限码（前端 hasPermission 需要）
        result.get("_standalone").put("actions", standalone);

        return result;
    }

    // ==================== 权限分配 CRUD ====================

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

    // ==================== 内部工具 ====================

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
     * 从所有角色的菜单自动同步细粒度权限（含列权限），确保新增权限授给已分配该菜单的角色。
     */
    private void syncRolePermissionsFromMenus() {
        List<PermissionVO> allPermissions = systemPermissionMapper.selectAllActivePermissions();
        Map<Long, PermissionVO> permissionByMenu = new LinkedHashMap<>();
        for (PermissionVO permission : allPermissions) {
            if (permission.getMenuId() != null) {
                permissionByMenu.put(permission.getId(), permission);
            }
        }
        Map<Long, List<Long>> menuIdsByRole = roleMenuIdsByRole();
        Map<Long, String> menuPermissionCodes = menuPermissionCodesById();
        Set<Long> aiLogAnalyzeRoleIds = aiLogAnalyzeRoleIds();
        for (Map.Entry<Long, List<Long>> entry : menuIdsByRole.entrySet()) {
            List<Long> existing = systemPermissionMapper.selectRolePermissionIds(entry.getKey());
            Set<Long> existingSet = new LinkedHashSet<>(existing);
            for (PermissionVO permission : permissionByMenu.values()) {
                if (!entry.getValue().contains(permission.getMenuId()) || existingSet.contains(permission.getId())) {
                    continue;
                }
                if ("ai:log:analyze".equals(permission.getPermissionCode()) && !aiLogAnalyzeRoleIds.contains(entry.getKey())) {
                    continue;
                }
                // 敏感列（sensitive_flag=1）仅 :manage 角色自动授权
                String menuPermissionCode = menuPermissionCodes.getOrDefault(permission.getMenuId(), "");
                if (Boolean.TRUE.equals(permission.getSensitiveFlag())) {
                    if (!isManagePermission(menuPermissionCode)) {
                        continue; // :view 角色不自动获得敏感列
                    }
                }
                systemPermissionMapper.insertRolePermission(idGenerator.nextId(), entry.getKey(), permission.getId());
            }
        }
    }

    private Map<Long, String> menuPermissionCodesById() {
        List<MenuVO> menus = systemPermissionMapper.selectAllActiveMenus();
        Map<Long, String> result = new LinkedHashMap<>();
        for (MenuVO menu : menus) {
            result.put(menu.getId(), menu.getPermissionCode());
        }
        return result;
    }

    private boolean isManagePermission(String permissionCode) {
        return permissionCode != null && permissionCode.endsWith(":manage");
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

    private Set<Long> aiLogAnalyzeRoleIds() {
        Set<Long> roleIds = new LinkedHashSet<>();
        for (Map<String, Object> role : systemPermissionMapper.selectAllRoles()) {
            Long roleId = toLong(role.get("id"));
            String roleCode = String.valueOf(role.get("roleCode"));
            if (roleId != null && ("ADMIN".equals(roleCode) || "AUDITOR".equals(roleCode) || "FINANCE_MANAGER".equals(roleCode))) {
                roleIds.add(roleId);
            }
        }
        return roleIds;
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
     * 确保每个菜单的权限目录完整（PAGE + BUTTON + COLUMN），缺失则插入
     */
    private void ensurePermissionCatalog() {
        List<MenuVO> menus = systemPermissionMapper.selectAllActiveMenus();
        for (MenuVO menu : menus) {
            if (!StringUtils.hasText(menu.getPermissionCode())) {
                continue;
            }
            String moduleCode = moduleFromCode(menu.getPermissionCode());
            // PAGE
            PermissionVO pagePermission = buildPermission(menu, menu.getPermissionCode(), menu.getName(), "PAGE",
                    actionFromCode(menu.getPermissionCode()), menu.getSortNo() * 100, false);
            insertPermissionIfMissing(pagePermission);
            // BUTTON
            for (String action : actionsFor(menu.getPermissionCode())) {
                String code = moduleCode + ":" + action;
                PermissionVO actionPermission = buildPermission(menu, code,
                        menu.getName() + "-" + ACTION_NAMES.getOrDefault(action, action),
                        "BUTTON", action, menu.getSortNo() * 100 + actionSort(action), false);
                insertPermissionIfMissing(actionPermission);
            }
            // AI 助手特殊权限
            if ("/ai-assistant".equals(menu.getPath())) {
                PermissionVO logAnalyzePermission = buildPermission(menu, "ai:log:analyze",
                        "AI助手-日志分析", "BUTTON", "log:analyze", menu.getSortNo() * 100 + 80, false);
                insertPermissionIfMissing(logAnalyzePermission);
            }
            // COLUMN —— 仅对有标准列定义的模块生成
            List<StandardColumnRegistry.ColumnDef> columnDefs = columnRegistry.columns(moduleCode);
            if (!columnDefs.isEmpty()) {
                int colIndex = 0;
                for (StandardColumnRegistry.ColumnDef col : columnDefs) {
                    String colCode = moduleCode + ":column:" + col.fieldName();
                    PermissionVO colPermission = buildPermission(menu, colCode,
                            menu.getName() + "-" + col.label() + "（列）",
                            "COLUMN", "column:" + col.fieldName(),
                            menu.getSortNo() * 100 + 100 + colIndex, col.sensitive());
                    insertPermissionIfMissing(colPermission);
                    colIndex++;
                }
            }
        }
    }

    private PermissionVO buildPermission(MenuVO menu, String code, String name, String type, String action,
                                         int sortNo, boolean sensitive) {
        PermissionVO permission = new PermissionVO();
        permission.setId(idGenerator.nextId());
        permission.setPermissionCode(code);
        permission.setPermissionName(name);
        permission.setPermissionType(type);
        permission.setModuleCode(moduleFromCode(code));
        permission.setActionCode(action);
        permission.setMenuId(menu.getId());
        permission.setSensitiveFlag(sensitive);
        permission.setSortNo(sortNo);
        return permission;
    }

    private void insertPermissionIfMissing(PermissionVO permission) {
        if (systemPermissionMapper.countPermissionByCode(permission.getPermissionCode()) > 0) {
            // 权限码是稳定主键；菜单、模块、动作和敏感标识可能随版本演进，需要在启动时增量修正。
            systemPermissionMapper.updatePermissionMetadata(permission);
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
        node.setSensitiveFlag(permission.getSensitiveFlag());
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

    // ==================== 权限码展开 ====================

    private LinkedHashSet<String> expandPermissionCodes(List<String> permissionCodes) {
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        for (String permissionCode : permissionCodes) {
            if (!StringUtils.hasText(permissionCode)) {
                continue;
            }
            permissionCode = normalizePermissionCode(permissionCode);
            expanded.add(permissionCode);
            String moduleCode = moduleFromCode(permissionCode);
            // 展开 action
            for (String action : actionsFor(permissionCode)) {
                expanded.add(moduleCode + ":" + action);
            }
            // 展开列权限
            List<StandardColumnRegistry.ColumnDef> columnDefs = columnRegistry.columns(moduleCode);
            if (!columnDefs.isEmpty()) {
                boolean isManage = permissionCode.endsWith(":manage");
                boolean isView = permissionCode.endsWith(":view");
                if (isManage || isView) {
                    for (StandardColumnRegistry.ColumnDef col : columnDefs) {
                        if (isView && col.sensitive()) {
                            continue; // :view 不展开敏感列
                        }
                        expanded.add(moduleCode + ":column:" + col.fieldName());
                    }
                }
            }
        }
        return expanded;
    }

    /**
     * 从 DENY 权限码中提取模块前缀，用于批量移除。
     * <ul>
     *   <li>{@code order:manage} → {@code "order:"}（移除所有 order:*）</li>
     *   <li>{@code order:view} → {@code "order:"}</li>
     *   <li>{@code order:query} → {@code "order:query"}（精确匹配）</li>
     *   <li>{@code order:column:total_amount} → {@code "order:column:total_amount"}（精确匹配）</li>
     * </ul>
     */
    private String extractDenyModulePrefix(String code) {
        if (code.endsWith(":manage") || code.endsWith(":view")) {
            return code.substring(0, code.lastIndexOf(':')) + ":";
        }
        return code;
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
            return Arrays.asList("chat", "conversation:query", "conversation:archive", "conversation:delete",
                    "memory:query", "memory:delete", "memory:settings");
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
        int columnIndex = permissionCode.indexOf(COLUMN_PERMISSION_MARKER);
        if (columnIndex > 0) {
            return permissionCode.substring(0, columnIndex);
        }
        int index = permissionCode.lastIndexOf(':');
        return index < 0 ? permissionCode : permissionCode.substring(0, index);
    }

    private String actionFromCode(String permissionCode) {
        int columnIndex = permissionCode.indexOf(COLUMN_PERMISSION_MARKER);
        if (columnIndex > 0) {
            // 列权限形如 order:column:order_no，动作必须保留 column: 前缀，
            // structuredPermissions 才能把它归入 columns，而不是误当成独立权限。
            return "column:" + permissionCode.substring(columnIndex + COLUMN_PERMISSION_MARKER.length());
        }
        int index = permissionCode.lastIndexOf(':');
        return index < 0 ? permissionCode : permissionCode.substring(index + 1);
    }

    private int actionSort(String action) {
        int index = MANAGE_ACTIONS.indexOf(action);
        return index < 0 ? 99 : index + 1;
    }

    // ==================== 菜单定义 ====================

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

    // ==================== 基础工具 ====================

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
        map.put("conversation:archive", "会话归档");
        map.put("conversation:delete", "会话删除");
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
