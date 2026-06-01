package jimmy.mapper;

import jimmy.model.MenuVO;
import jimmy.model.PermissionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Mapper
public interface SystemPermissionMapper {

    List<MenuVO> selectAllActiveMenus();

    List<Long> selectRoleMenuIds(@Param("roleId") Long roleId);

    int countRoleById(@Param("roleId") Long roleId);

    List<Long> selectAllRoleIds();

    List<Map<String, Object>> selectAllRoles();

    int countRoleMenus(@Param("roleId") Long roleId);

    int countMenuById(@Param("menuId") Long menuId);

    int countMenuByPath(@Param("menuPath") String menuPath);

    Long selectMenuIdByPath(@Param("menuPath") String menuPath);

    int insertMenu(@Param("id") Long id, @Param("parentId") Long parentId, @Param("menuName") String menuName,
                   @Param("menuPath") String menuPath, @Param("permissionCode") String permissionCode,
                   @Param("sortNo") Integer sortNo, @Param("createTime") Timestamp createTime,
                   @Param("updateTime") Timestamp updateTime);

    int countPermissionMenu();

    Long selectSystemMenuId();

    int deleteRoleMenus(@Param("roleId") Long roleId);

    int insertRoleMenu(@Param("id") Long id, @Param("roleId") Long roleId, @Param("menuId") Long menuId);

    int insertPermissionMenu(@Param("id") Long id, @Param("parentId") Long parentId,
                             @Param("createTime") Timestamp createTime,
                             @Param("updateTime") Timestamp updateTime);

    int createPermissionTable();

    int createRolePermissionTable();

    int createUserPermissionTable();

    List<PermissionVO> selectAllActivePermissions();

    List<Long> selectRolePermissionIds(@Param("roleId") Long roleId);

    List<Long> selectUserPermissionIds(@Param("userId") Long userId, @Param("grantType") String grantType);

    List<String> selectRolePermissionCodes(@Param("roleId") Long roleId);

    List<Map<String, Object>> selectUserPermissionOverrides(@Param("userId") Long userId);

    int countUserById(@Param("userId") Long userId);

    int countPermissionByCode(@Param("permissionCode") String permissionCode);

    int countPermissionById(@Param("permissionId") Long permissionId);

    int insertPermission(PermissionVO permission);

    int deleteRolePermissions(@Param("roleId") Long roleId);

    int insertRolePermission(@Param("id") Long id, @Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    int deleteUserPermissions(@Param("userId") Long userId);

    int insertUserPermission(@Param("id") Long id, @Param("userId") Long userId,
                             @Param("permissionId") Long permissionId, @Param("grantType") String grantType,
                             @Param("createTime") Timestamp createTime, @Param("updateTime") Timestamp updateTime);
}
