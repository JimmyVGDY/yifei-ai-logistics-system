package jimmy.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.model.MenuVO;
import jimmy.model.RoleMenuUpdateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SystemPermissionService {

    private final JdbcTemplate jdbcTemplate;
    private final CompactSnowflakeIdGenerator idGenerator;

    public SystemPermissionService(JdbcTemplate jdbcTemplate, CompactSnowflakeIdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
    }

    public List<MenuVO> menuTree() {
        ensurePermissionMenu();
        List<MenuVO> rows = jdbcTemplate.query(
                "select id, parent_id, menu_name, menu_path, permission_code, sort_no from sys_menu " +
                        "where status = 1 order by sort_no, id",
                (rs, rowNum) -> {
                    MenuVO menu = new MenuVO();
                    menu.setId(rs.getLong("id"));
                    menu.setParentId(rs.getLong("parent_id"));
                    menu.setName(rs.getString("menu_name"));
                    menu.setPath(rs.getString("menu_path"));
                    menu.setPermissionCode(rs.getString("permission_code"));
                    menu.setSortNo(rs.getInt("sort_no"));
                    return menu;
                }
        );
        return buildTree(rows);
    }

    public List<Long> roleMenuIds(long roleId) {
        ensurePermissionMenu();
        return jdbcTemplate.queryForList("select menu_id from sys_role_menu where role_id = ? order by menu_id", Long.class, roleId);
    }

    @Transactional
    public List<Long> updateRoleMenus(long roleId, RoleMenuUpdateRequest request) {
        List<Long> menuIds = request == null || request.getMenuIds() == null ? new ArrayList<>() : request.getMenuIds();
        Integer roleCount = jdbcTemplate.queryForObject("select count(1) from sys_role where id = ?", Integer.class, roleId);
        if (roleCount == null || roleCount == 0) {
            throw new IllegalArgumentException("角色不存在");
        }
        jdbcTemplate.update("delete from sys_role_menu where role_id = ?", roleId);
        for (Long menuId : menuIds) {
            if (menuId == null || !menuExists(menuId)) {
                continue;
            }
            jdbcTemplate.update("insert into sys_role_menu (id, role_id, menu_id) values (?, ?, ?)",
                    idGenerator.nextId(), roleId, menuId);
        }
        log.info("角色权限配置已更新，roleId={}, menuCount={}, operator={}", roleId, menuIds.size(), StpUtil.getLoginIdDefaultNull());
        return roleMenuIds(roleId);
    }

    private boolean menuExists(Long menuId) {
        Integer count = jdbcTemplate.queryForObject("select count(1) from sys_menu where id = ? and status = 1", Integer.class, menuId);
        return count != null && count > 0;
    }

    private List<MenuVO> buildTree(List<MenuVO> rows) {
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
        Integer exists = jdbcTemplate.queryForObject(
                "select count(1) from sys_menu where menu_path = '/system/permissions'",
                Integer.class
        );
        if (exists != null && exists > 0) {
            return;
        }
        List<Long> parentIds = jdbcTemplate.queryForList("select id from sys_menu where menu_path = '/system' order by id limit 1", Long.class);
        Long parentId = parentIds.isEmpty() ? 0L : parentIds.get(0);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        jdbcTemplate.update(
                "insert into sys_menu (id, parent_id, menu_name, menu_path, permission_code, sort_no, status, create_time, update_time) " +
                        "values (?, ?, '权限配置', '/system/permissions', 'system:permission:manage', 925, 1, ?, ?)",
                idGenerator.nextId(), parentId, now, now
        );
    }
}
