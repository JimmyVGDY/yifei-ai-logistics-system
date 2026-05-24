package jimmy.controller;

import jimmy.entity.DemoUser;
import jimmy.service.DemoUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/demo-users")
public class DemoUserController {

    private final DemoUserService demoUserService;

    public DemoUserController(DemoUserService demoUserService) {
        this.demoUserService = demoUserService;
    }

    @GetMapping
    public List<DemoUser> findAll() {
        return demoUserService.findAll();
    }

    @GetMapping("/detail")
    public DemoUser findById(@RequestParam Long id) {
        return demoUserService.findById(id);
    }

    @PostMapping
    public DemoUser create(@RequestParam String username,
                           @RequestParam String displayName) {
        return demoUserService.create(username, displayName);
    }
}
