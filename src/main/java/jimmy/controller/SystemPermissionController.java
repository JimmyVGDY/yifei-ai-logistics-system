package jimmy.controller;

import jimmy.logistics.annotation.OperationLog;
import jimmy.model.ApiResponse;
import jimmy.model.MenuVO;
import jimmy.model.RoleMenuUpdateRequest;
import jimmy.service.SystemPermissionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system/permissions")
public class SystemPermissionController {

    private final SystemPermissionService systemPermissionService;

    public SystemPermissionController(SystemPermissionService systemPermissionService) {
        this.systemPermissionService = systemPermissionService;
    }

    @GetMapping("/menus")
    public ApiResponse<List<MenuVO>> menus() {
        return ApiResponse.success(systemPermissionService.menuTree());
    }

    @GetMapping("/roles/{roleId}/menus")
    public ApiResponse<List<Long>> roleMenus(@PathVariable long roleId) {
        return ApiResponse.success(systemPermissionService.roleMenuIds(roleId));
    }

    @OperationLog("配置角色权限")
    @PostMapping("/roles/{roleId}/menus")
    public ApiResponse<List<Long>> updateRoleMenus(@PathVariable long roleId,
                                                   @RequestBody RoleMenuUpdateRequest request) {
        return ApiResponse.success(systemPermissionService.updateRoleMenus(roleId, request));
    }
}
