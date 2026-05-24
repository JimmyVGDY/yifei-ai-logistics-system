package jimmy.mapper;

import jimmy.entity.DemoUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DemoUserMapper {

    List<DemoUser> findAll();

    DemoUser findById(@Param("id") Long id);

    int insert(DemoUser demoUser);
}
