package jimmy.service;

import jimmy.entity.DemoUser;
import jimmy.mapper.DemoUserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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
