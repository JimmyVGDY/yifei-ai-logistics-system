package jimmy.system.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限树节点，包含菜单分组、页面/按钮权限和列权限。
 */
public class PermissionTreeNodeVO {

    /** 节点 ID：菜单 ID、权限 ID 或虚拟分组 ID。 */
    private Long id;
    /** 节点显示名称。 */
    private String label;
    /** 节点类型：MENU / PAGE / BUTTON / COLUMN_GROUP / COLUMN。 */
    private String nodeType;
    /** 权限码。 */
    private String permissionCode;
    /** 权限 ID，仅真实权限节点有值。 */
    private Long permissionId;
    /** 所属菜单 ID。 */
    private Long menuId;
    /** 是否敏感列，仅 COLUMN 节点有业务意义。 */
    private Boolean sensitiveFlag;
    /** 子节点列表。 */
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

    public Boolean getSensitiveFlag() { return sensitiveFlag; }

    public void setSensitiveFlag(Boolean sensitiveFlag) { this.sensitiveFlag = sensitiveFlag; }

    public List<PermissionTreeNodeVO> getChildren() { return children; }

    public void setChildren(List<PermissionTreeNodeVO> children) { this.children = children; }
}
