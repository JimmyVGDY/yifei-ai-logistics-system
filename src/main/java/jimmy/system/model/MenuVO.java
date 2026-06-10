package jimmy.system.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点 —— 支持父子嵌套，前端构建左侧侧边栏菜单。
 */
public class MenuVO {

    /** 菜单ID */
    private Long id;
    /** 父菜单ID（0=顶级菜单） */
    private Long parentId;
    /** 菜单名称 */
    private String name;
    /** 前端路由路径 */
    private String path;
    /** 关联的权限码 */
    private String permissionCode;
    /** 排序序号 */
    private Integer sortNo;
    /** 子菜单列表 */
    private List<MenuVO> children = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public List<MenuVO> getChildren() {
        return children;
    }

    public void setChildren(List<MenuVO> children) {
        this.children = children;
    }
}
