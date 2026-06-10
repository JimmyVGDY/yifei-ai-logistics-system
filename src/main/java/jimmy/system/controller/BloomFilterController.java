package jimmy.system.controller;

import jimmy.common.model.ApiResponse;
import jimmy.system.model.InfrastructureStatus;
import jimmy.system.service.BloomFilterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 布隆过滤器调试接口 —— 用于验证订单号是否已被记录。
 */
@RestController
@RequestMapping("/bloom-filter")
public class BloomFilterController {

    private final BloomFilterService bloomFilterService;

    public BloomFilterController(BloomFilterService bloomFilterService) {
        this.bloomFilterService = bloomFilterService;
    }

    @PostMapping("/items")
    public ApiResponse<InfrastructureStatus> put(@RequestParam String value) {
        boolean changed = bloomFilterService.put(value);
        return ApiResponse.success(InfrastructureStatus.of("bloom-filter", "stored")
                .detail("value", value).detail("changed", changed));
    }

    @GetMapping("/items")
    public ApiResponse<InfrastructureStatus> mightContain(@RequestParam String value) {
        boolean mightContain = bloomFilterService.mightContain(value);
        return ApiResponse.success(InfrastructureStatus.of("bloom-filter", "checked")
                .detail("value", value).detail("mightContain", mightContain));
    }
}
