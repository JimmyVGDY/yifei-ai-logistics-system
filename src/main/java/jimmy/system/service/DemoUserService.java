package jimmy.system.service;

import jimmy.system.entity.DemoUser;
import jimmy.system.mapper.DemoUserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Demo 用户示例服务 —— 验证 MyBatis + Spring Boot 基础 CRUD 链路。
 */
@Service
public class DemoUserService {

    private final DemoUserMapper demoUserMapper;

    public DemoUserService(DemoUserMapper demoUserMapper) {
        this.demoUserMapper = demoUserMapper;
    }

    public List<DemoUser> findAll() {
        return demoUserMapper.findAll();
    }

    public DemoUser findById(Long id) {
        return demoUserMapper.findById(id);
    }

    public DemoUser create(String username, String displayName) {
        DemoUser demoUser = new DemoUser();
        demoUser.setUsername(username);
        demoUser.setDisplayName(displayName);
        demoUser.setCreatedAt(LocalDateTime.now());
        demoUserMapper.insert(demoUser);
        return demoUser;
    }
}
