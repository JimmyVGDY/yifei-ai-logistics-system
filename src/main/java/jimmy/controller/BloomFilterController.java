package jimmy.controller;

import jimmy.model.InfrastructureStatus;
import jimmy.service.BloomFilterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bloom-filter")
public class BloomFilterController {

    private final BloomFilterService bloomFilterService;

    public BloomFilterController(BloomFilterService bloomFilterService) {
        this.bloomFilterService = bloomFilterService;
    }

    @PostMapping("/items")
    public InfrastructureStatus put(@RequestParam String value) {
        boolean changed = bloomFilterService.put(value);
        return InfrastructureStatus.of("bloom-filter", "stored")
                .detail("value", value)
                .detail("changed", changed);
    }

    @GetMapping("/items")
    public InfrastructureStatus mightContain(@RequestParam String value) {
        boolean mightContain = bloomFilterService.mightContain(value);
        return InfrastructureStatus.of("bloom-filter", "checked")
                .detail("value", value)
                .detail("mightContain", mightContain);
    }
}
