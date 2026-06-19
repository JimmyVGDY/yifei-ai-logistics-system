package jimmy.system.model;

/**
 * 权限码扁平视图 —— 用于角色/用户权限列表展示。
 */
public class PermissionVO {

    /** 权限ID */
    private Long id;
    /** 权限码（如 logistics:order:create） */
    private String permissionCode;
    /** 权限名称 */
    private String permissionName;
    /** 权限类型（API/BUTTON/MENU） */
    private String permissionType;
    /** 模块编码 */
    private String moduleCode;
    /** 操作编码（view/create/update/delete 等） */
    private String actionCode;
    /** 关联菜单ID */
    private Long menuId;
    /** 敏感列标记（仅 COLUMN 类型使用，:view 不自动授权） */
    private Boolean sensitiveFlag;

    /** 排序序号 */
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

    public Boolean getSensitiveFlag() { return sensitiveFlag; }

    public void setSensitiveFlag(Boolean sensitiveFlag) { this.sensitiveFlag = sensitiveFlag; }

    public Integer getSortNo() { return sortNo; }

    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
