package jimmy.model;

import java.util.ArrayList;
import java.util.List;

public class UserPermissionVO {

    private List<Long> grantPermissionIds = new ArrayList<>();
    private List<Long> denyPermissionIds = new ArrayList<>();

    public UserPermissionVO() {
    }

    public UserPermissionVO(List<Long> grantPermissionIds, List<Long> denyPermissionIds) {
        this.grantPermissionIds = grantPermissionIds;
        this.denyPermissionIds = denyPermissionIds;
    }

    public List<Long> getGrantPermissionIds() { return grantPermissionIds; }

    public void setGrantPermissionIds(List<Long> grantPermissionIds) { this.grantPermissionIds = grantPermissionIds; }

    public List<Long> getDenyPermissionIds() { return denyPermissionIds; }

    public void setDenyPermissionIds(List<Long> denyPermissionIds) { this.denyPermissionIds = denyPermissionIds; }
}
