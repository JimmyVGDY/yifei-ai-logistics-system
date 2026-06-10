package jimmy.system.mapper;

import jimmy.system.entity.DemoUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Demo 用户数据访问 —— 项目初始化时的示例模块，仅用于验证持久层配置。
 */
@Mapper
public interface DemoUserMapper {

    List<DemoUser> findAll();

    DemoUser findById(@Param("id") Long id);

    int insert(DemoUser demoUser);
}
