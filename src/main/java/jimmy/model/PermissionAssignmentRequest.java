package jimmy.model;

import java.util.ArrayList;
import java.util.List;

public class PermissionAssignmentRequest {

    private List<Long> permissionIds = new ArrayList<>();
    private List<Long> grantPermissionIds = new ArrayList<>();
    private List<Long> denyPermissionIds = new ArrayList<>();

    public List<Long> getPermissionIds() { return permissionIds; }

    public void setPermissionIds(List<Long> permissionIds) { this.permissionIds = permissionIds; }

    public List<Long> getGrantPermissionIds() { return grantPermissionIds; }

    public void setGrantPermissionIds(List<Long> grantPermissionIds) { this.grantPermissionIds = grantPermissionIds; }

    public List<Long> getDenyPermissionIds() { return denyPermissionIds; }

    public void setDenyPermissionIds(List<Long> denyPermissionIds) { this.denyPermissionIds = denyPermissionIds; }
}
