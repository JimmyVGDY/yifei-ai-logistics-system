package jimmy.model;

import java.util.List;

/**
 * 用户特殊权限视图 —— 独立的授权列表(grantPermissionIds)和拒绝列表(denyPermissionIds)。
 */
public record UserPermissionVO(List<Long> grantPermissionIds, List<Long> denyPermissionIds) {

    public UserPermissionVO {
        grantPermissionIds = grantPermissionIds == null ? List.of() : grantPermissionIds;
        denyPermissionIds = denyPermissionIds == null ? List.of() : denyPermissionIds;
    }

    public UserPermissionVO() {
        this(List.of(), List.of());
    }

    public List<Long> getGrantPermissionIds() { return grantPermissionIds; }

    public List<Long> getDenyPermissionIds() { return denyPermissionIds; }
}
