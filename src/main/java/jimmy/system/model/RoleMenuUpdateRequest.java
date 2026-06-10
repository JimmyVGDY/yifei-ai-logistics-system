package jimmy.system.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色菜单更新请求 —— 保存权限配置时使用"全量替换"策略。
 * <p>
 * 后端先删除该角色所有已有菜单关联，再写入请求中传入的菜单 ID 列表，
 * 确保取消勾选的菜单也会移除授权。
 * </p>
 */
public class RoleMenuUpdateRequest {

    /** 角色需要关联的菜单 ID 列表（全量，非增量） */
    private List<Long> menuIds = new ArrayList<>();

    public List<Long> getMenuIds() { return menuIds; }

    public void setMenuIds(List<Long> menuIds) { this.menuIds = menuIds; }
}
