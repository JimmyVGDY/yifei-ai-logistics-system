package jimmy.model;

import java.util.ArrayList;
import java.util.List;

public class RoleMenuUpdateRequest {

    private List<Long> menuIds = new ArrayList<>();

    public List<Long> getMenuIds() {
        return menuIds;
    }

    public void setMenuIds(List<Long> menuIds) {
        this.menuIds = menuIds;
    }
}
