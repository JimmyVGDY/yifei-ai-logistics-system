package jimmy.model;

/**
 * 权限码扁平视图 —— 用于角色/用户权限列表展示。
 */
public class PermissionVO {

    private Long id;
    private String permissionCode;
    private String permissionName;
    private String permissionType;
    private String moduleCode;
    private String actionCode;
    private Long menuId;
    private Integer sortNo;

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public String getPermissionCode() { return permissionCode; }

    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }

    public String getPermissionName() { return permissionName; }

    public void setPermissionName(String permissionName) { this.permissionName = permissionName; }

    public String getPermissionType() { return permissionType; }

    public void setPermissionType(String permissionType) { this.permissionType = permissionType; }

    public String getModuleCode() { return moduleCode; }

    public void setModuleCode(String moduleCode) { this.moduleCode = moduleCode; }

    public String getActionCode() { return actionCode; }

    public void setActionCode(String actionCode) { this.actionCode = actionCode; }

    public Long getMenuId() { return menuId; }

    public void setMenuId(Long menuId) { this.menuId = menuId; }

    public Integer getSortNo() { return sortNo; }

    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
