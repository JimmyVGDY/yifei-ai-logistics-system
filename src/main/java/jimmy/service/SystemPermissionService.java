package jimmy.service;

import cn.dev33.satoken.stp.StpUtil;
import jimmy.common.id.CompactSnowflakeIdGenerator;
import jimmy.mapper.SystemPermissionMapper;
import jimmy.model.MenuVO;
import jimmy.model.RoleMenuUpdateRequest;
import lombok.extern.slf4j.Slf4j;
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

    private final SystemPermissionMapper systemPermissionMapper;
    private final CompactSnowflakeIdGenerator idGenerator;

    public SystemPermissionService(SystemPermissionMapper systemPermissionMapper,
                                   CompactSnowflakeIdGenerator idGenerator) {
        this.systemPermissionMapper = systemPermissionMapper;
        this.idGenerator = idGenerator;
    }

    public List<MenuVO> menuTree() {
        ensurePermissionMenu();
        return buildTree(systemPermissionMapper.selectAllActiveMenus());
    }

    public List<Long> roleMenuIds(long roleId) {
        ensurePermissionMenu();
        return systemPermissionMapper.selectRoleMenuIds(roleId);
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
        log.info("角色权限配置已更新，roleId={}, menuCount={}, operator={}", roleId, menuIds.size(), StpUtil.getLoginIdDefaultNull());
        return roleMenuIds(roleId);
    }

    private boolean menuExists(Long menuId) {
        return systemPermissionMapper.countMenuById(menuId) > 0;
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
        Long parentId = systemPermissionMapper.selectSystemMenuId();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Long resolvedParentId = parentId == null ? 0L : parentId;
        if (systemPermissionMapper.countPermissionMenu() == 0) {
            systemPermissionMapper.insertPermissionMenu(idGenerator.nextId(), resolvedParentId, now, now);
        }
        if (systemPermissionMapper.countStructuredLogMenu() == 0) {
            systemPermissionMapper.insertStructuredLogMenu(idGenerator.nextId(), resolvedParentId, now, now);
        }
    }
}
