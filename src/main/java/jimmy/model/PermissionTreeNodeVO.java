package jimmy.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限树节点 —— 含菜单分组 + 细粒度权限项，用于前端权限配置页面树形展示。
 */
public class PermissionTreeNodeVO {

    private Long id;
    private String label;
    private String nodeType;
    private String permissionCode;
    private Long permissionId;
    private Long menuId;
    private List<PermissionTreeNodeVO> children = new ArrayList<>();

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public String getLabel() { return label; }

    public void setLabel(String label) { this.label = label; }

    public String getNodeType() { return nodeType; }

    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getPermissionCode() { return permissionCode; }

    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }

    public Long getPermissionId() { return permissionId; }

    public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }

    public Long getMenuId() { return menuId; }

    public void setMenuId(Long menuId) { this.menuId = menuId; }

    public List<PermissionTreeNodeVO> getChildren() { return children; }

    public void setChildren(List<PermissionTreeNodeVO> children) { this.children = children; }
}
