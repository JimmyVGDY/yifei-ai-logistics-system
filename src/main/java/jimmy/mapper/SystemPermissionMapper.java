package jimmy.mapper;

import jimmy.model.MenuVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface SystemPermissionMapper {

    List<MenuVO> selectAllActiveMenus();

    List<Long> selectRoleMenuIds(@Param("roleId") Long roleId);

    int countRoleById(@Param("roleId") Long roleId);

    int countMenuById(@Param("menuId") Long menuId);

    int countPermissionMenu();

    Long selectSystemMenuId();

    int deleteRoleMenus(@Param("roleId") Long roleId);

    int insertRoleMenu(@Param("id") Long id, @Param("roleId") Long roleId, @Param("menuId") Long menuId);

    int insertPermissionMenu(@Param("id") Long id, @Param("parentId") Long parentId,
                             @Param("createTime") Timestamp createTime,
                             @Param("updateTime") Timestamp updateTime);
}
