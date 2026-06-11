package jimmy.auth.service;

import jimmy.auth.mapper.AuthMapper;
import jimmy.system.model.MenuVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 菜单树构建器 —— 从扁平菜单数据按 parentId 组装为前端侧边栏所需的树形结构。
 * <p>
 * 从 {@link AuthService} 拆分，独立管理菜单查询、权限展开和角色默认菜单逻辑。
 * 核心职责：
 * <ol>
 *   <li>查询角色菜单（直接分配 + 权限码匹配）</li>
 *   <li>权限码展开（manage → create/update/delete/query/export/import）</li>
 *   <li>默认菜单兜底（确保所有角色至少能看到基本页面）</li>
 *   <li>将扁平菜单按 parentId 递归组装为树</li>
 * </ol>
 */
@Component
public class MenuTreeBuilder {

    private final AuthMapper authMapper;

    public MenuTreeBuilder(AuthMapper authMapper) {
        this.authMapper = authMapper;
    }

    /**
     * 查询并组装角色对应的菜单树。
     * <p>
     * 合并 sys_role_menu 直接分配的菜单和通过权限码匹配的菜单，
     * 按 parentId 递归挂载为树结构，供前端侧边栏渲染。
     *
     * @param roleId      角色 ID
     * @param permissions 角色有效权限码列表
     * @return 菜单树（仅含根节点）
     */
    public List<MenuVO> queryMenus(Long roleId, List<String> permissions) {
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

    /**
     * 为角色补充只读查询权限，用于加载关联下拉数据。
     * <p>
     * 不同角色需要查询不同的关联模块数据（如调度员需要查订单和运单），
     * 这里只补充 :query 权限，不额外开放菜单入口。
     */
    public void addRelationQueryPermissions(String roleCode, List<String> permissions) {
        String[] prefixes = switch (String.valueOf(roleCode)) {
            case "OPERATIONS_MANAGER" -> new String[]{"customer", "driver", "vehicle", "fee"};
            case "ORDER_OPERATOR", "CUSTOMER_SERVICE" -> new String[]{"customer", "order", "waybill", "track"};
            case "DISPATCHER" -> new String[]{"order", "waybill"};
            case "FLEET_MANAGER", "DRIVER" -> new String[]{"order", "waybill", "dispatch", "driver", "vehicle"};
            case "EXCEPTION_HANDLER" -> new String[]{"order", "task", "track"};
            case "FINANCE" -> new String[]{"order"};
            case "FINANCE_MANAGER" -> new String[]{"order", "fee"};
            case "AUDITOR" -> new String[]{"order", "waybill", "track", "fee", "system:log"};
            default -> new String[0];
        };
        if (prefixes.length > 0) {
            for (String prefix : prefixes) {
                permissions.add(prefix + ":query");
            }
        }
    }

    /**
     * 展开权限码列表：将 {@code :manage} 展开为增删改查导等细粒度权限，
     * 将 {@code :view} 展开为查询+导出权限。
     */
    public void expandActionPermissions(String permission, List<String> expanded) {
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

    /**
     * 列表去重，保持插入顺序。
     */
    public List<String> distinctList(List<String> list) {
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (!result.contains(s)) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * 从权限码中提取模块前缀（最后一个冒号之前的部分）。
     */
    public String moduleFromPermission(String permissionCode) {
        int index = permissionCode.lastIndexOf(':');
        return index < 0 ? permissionCode : permissionCode.substring(0, index);
    }

    /**
     * 合并默认菜单与数据库菜单。
     * <p>
     * 默认菜单只作为兜底；如果数据库里配置了额外菜单，会在后面追加进来。
     */
    public List<MenuVO> mergeDefaultMenus(String roleCode, List<MenuVO> dbMenus) {
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

    /**
     * 从菜单树中收集所有权限码并展开。
     */
    public List<String> collectPermissions(String roleCode, List<MenuVO> menus) {
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
        // 部分角色页面需要加载关联下拉数据，只补充只读查询权限。
        addRelationQueryPermissions(roleCode, expanded);
        return distinctList(expanded);
    }

    /**
     * 将菜单树扁平化为列表（深度优先）。
     */
    public List<MenuVO> flattenMenus(List<MenuVO> menus) {
        List<MenuVO> flattened = new ArrayList<>();
        for (MenuVO menu : menus) {
            flattened.add(menu);
            if (menu.getChildren() != null && !menu.getChildren().isEmpty()) {
                flattened.addAll(flattenMenus(menu.getChildren()));
            }
        }
        return flattened;
    }

    // ==================== 内部实现 ====================

    /**
     * 从权限列表中提取模块前缀，生成 :manage 和 :view 两类通配权限码，
     * 用于匹配未直接分配但模块相关的菜单。
     */
    private List<String> menuLookupPermissionCodes(List<String> permissions) {
        LinkedHashSet<String> codes = new LinkedHashSet<>(permissions);
        for (String permission : permissions) {
            String module = moduleFromPermission(permission);
            codes.add(module + ":manage");
            codes.add(module + ":view");
        }
        return new ArrayList<>(codes);
    }

    /**
     * 判断菜单是否应对当前用户显示。
     * <p>
     * 无 permissionCode 的菜单直接显示；有 permissionCode 时检查权限列表中
     * 是否精确匹配或模块前缀匹配。
     */
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

    /**
     * 获取角色对应的默认菜单（硬编码兜底）。
     * <p>
     * 当数据库未配置某角色菜单时，通过默认菜单确保系统基本可用。
     */
    private List<MenuVO> defaultMenus(String roleCode) {
        return switch (String.valueOf(roleCode)) {
            case "CUSTOMER_SERVICE" -> menus("customers", "orders", "waybills", "tracks");
            case "ORDER_OPERATOR" -> menus("orders", "customers", "waybills", "tracks");
            case "DISPATCHER" -> menus("dispatches", "tasks", "drivers", "vehicles", "tracks", "exceptions");
            case "FLEET_MANAGER" -> menus("drivers", "vehicles", "dispatches", "tasks", "tracks");
            case "DRIVER" -> menus("tasks", "tracks", "exceptions");
            case "EXCEPTION_HANDLER" -> menus("exceptions", "orders", "tasks", "tracks");
            case "FINANCE" -> menus("fees", "dashboard");
            case "FINANCE_MANAGER" -> menus("fees", "dashboard", "system", "operationLogs");
            case "OPERATIONS_MANAGER" ->
                    menus("dashboard", "orders", "waybills", "dispatches", "tasks", "tracks", "exceptions");
            case "AUDITOR" -> menus("dashboard", "orders", "waybills", "tracks", "fees", "system", "operationLogs");
            case "FILE_MANAGER" -> menus("files", "resources");
            case "CUSTOMER" -> menus("orders", "tracks");
            default -> new ArrayList<>();
        };
    }

    /**
     * 根据 key 集合从默认菜单总表中按需取出菜单项。
     * <p>
     * 将 defaultMenus 中指定的 key 映射到 allDefaultMenus 中的实际菜单对象，
     * 跳过总表中不存在的 key（安全兜底）。
     *
     * @param keys 菜单 key 数组（如 "orders", "customers"）
     * @return 对应的菜单对象列表
     */
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

    /**
     * 构建全部默认菜单对象的总映射表。
     * <p>
     * 使用负值 ID 避免与数据库中的真实菜单 ID 冲突，
     * sortNo 控制侧边栏排序（业务菜单从小到大，系统管理 900+、AI 助手 960）。
     *
     * @return key → MenuVO 的映射（key 如 "orders", "customers"）
     */
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
        menus.put("ai", menu(-19L, 0L, "AI助手", "/ai-assistant", "ai:chat", 960));
        return menus;
    }

    /**
     * 快捷构建单个菜单对象。
     * <p>
     * 用于 allDefaultMenus 中统一构造默认菜单，减少重复赋值代码。
     *
     * @param id             菜单 ID（默认菜单使用负数，避免冲突）
     * @param parentId       父菜单 ID（0 表示根节点）
     * @param name           菜单显示名称
     * @param path           前端路由路径
     * @param permissionCode 权限码
     * @param sortNo         排序号
     * @return 构建好的 MenuVO 对象
     */
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
}
