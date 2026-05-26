package jimmy.mapper;

import jimmy.model.MenuVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AuthMapper {

    Map<String, Object> findLoginUserByUsername(@Param("username") String username);

    Map<String, Object> findLoginUserById(@Param("userId") Long userId);

    int updatePassword(@Param("userId") Long userId, @Param("password") String password);

    List<MenuVO> selectMenusByRoleId(@Param("roleId") Long roleId);
}
