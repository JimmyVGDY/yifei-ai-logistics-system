package jimmy.controller;

import jimmy.entity.DemoUser;
import jimmy.model.ApiResponse;
import jimmy.service.DemoUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Demo 用户示例控制器 —— 验证项目基础 CRUD 功能是否正常。
 */
@RestController
@RequestMapping("/demo-users")
public class DemoUserController {

    private final DemoUserService demoUserService;

    public DemoUserController(DemoUserService demoUserService) {
        this.demoUserService = demoUserService;
    }

    @GetMapping
    public ApiResponse<List<DemoUser>> findAll() {
        return ApiResponse.success(demoUserService.findAll());
    }

    @GetMapping("/detail")
    public ApiResponse<DemoUser> findById(@RequestParam Long id) {
        return ApiResponse.success(demoUserService.findById(id));
    }

    @PostMapping
    public ApiResponse<DemoUser> create(@RequestParam String username,
                                        @RequestParam String displayName) {
        return ApiResponse.success(demoUserService.create(username, displayName));
    }
}
