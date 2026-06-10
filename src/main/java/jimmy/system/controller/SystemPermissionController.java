package jimmy.system.controller;

import jimmy.logistics.annotation.OperationLog;
import jimmy.common.model.ApiResponse;
import jimmy.system.model.MenuVO;
import jimmy.system.model.PermissionAssignmentRequest;
import jimmy.system.model.PermissionTreeNodeVO;
import jimmy.system.model.RoleMenuUpdateRequest;
import jimmy.system.model.UserPermissionVO;
import jimmy.system.service.SystemPermissionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统权限控制器 —— 菜单树/权限树查看、角色菜单/权限分配、用户特殊授权管理。
 * <p>
 * 所有写操作标注 {@link OperationLog} 记入审计日志。
 */
@RestController
@RequestMapping("/system/permissions")
public class SystemPermissionController {

    private final SystemPermissionService systemPermissionService;

    public SystemPermissionController(SystemPermissionService systemPermissionService) {
        this.systemPermissionService = systemPermissionService;
    }

    @OperationLog("权限配置-加载菜单树")
    @GetMapping("/menus")
    public ApiResponse<List<MenuVO>> menus() {
        return ApiResponse.success(systemPermissionService.menuTree());
    }

    @OperationLog("权限配置-加载权限树")
    @GetMapping("/tree")
    public ApiResponse<List<PermissionTreeNodeVO>> permissionTree() {
        return ApiResponse.success(systemPermissionService.permissionTree());
    }

    @OperationLog("权限配置-查看角色菜单")
    @GetMapping("/roles/{roleId}/menus")
    public ApiResponse<List<Long>> roleMenus(@PathVariable long roleId) {
        return ApiResponse.success(systemPermissionService.roleMenuIds(roleId));
    }

    @OperationLog("权限配置-查看角色权限")
    @GetMapping("/roles/{roleId}/permissions")
    public ApiResponse<List<Long>> rolePermissions(@PathVariable long roleId) {
        return ApiResponse.success(systemPermissionService.rolePermissionIds(roleId));
    }

    @OperationLog("配置角色权限")
    @PostMapping("/roles/{roleId}/menus")
    public ApiResponse<List<Long>> updateRoleMenus(@PathVariable long roleId,
                                                   @RequestBody RoleMenuUpdateRequest request) {
        return ApiResponse.success(systemPermissionService.updateRoleMenus(roleId, request));
    }

    @OperationLog("配置角色细粒度权限")
    @PostMapping("/roles/{roleId}/permissions")
    public ApiResponse<List<Long>> updateRolePermissions(@PathVariable long roleId,
                                                         @RequestBody PermissionAssignmentRequest request) {
        return ApiResponse.success(systemPermissionService.updateRolePermissions(roleId, request));
    }

    @OperationLog("权限配置-查看用户特殊权限")
    @GetMapping("/users/{userId}/permissions")
    public ApiResponse<UserPermissionVO> userPermissions(@PathVariable long userId) {
        return ApiResponse.success(systemPermissionService.userPermissionIds(userId));
    }

    @OperationLog("配置用户特殊权限")
    @PostMapping("/users/{userId}/permissions")
    public ApiResponse<UserPermissionVO> updateUserPermissions(@PathVariable long userId,
                                                               @RequestBody PermissionAssignmentRequest request) {
        return ApiResponse.success(systemPermissionService.updateUserPermissions(userId, request));
    }
}
