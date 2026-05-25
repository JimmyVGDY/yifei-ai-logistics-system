package jimmy.logistics.controller;

import jimmy.logistics.model.LogisticsDashboardSummary;
import jimmy.logistics.service.LogisticsRequirementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/logistics")
public class LogisticsRequirementController {

    private final LogisticsRequirementService logisticsRequirementService;

    public LogisticsRequirementController(LogisticsRequirementService logisticsRequirementService) {
        this.logisticsRequirementService = logisticsRequirementService;
    }

    @GetMapping("/dashboard")
    public LogisticsDashboardSummary dashboard() {
        return logisticsRequirementService.dashboardSummary();
    }

    @GetMapping("/modules/{module}")
    public List<Map<String, Object>> moduleRecords(@PathVariable String module,
                                                   @RequestParam(defaultValue = "20") int limit,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String startTime,
                                                   @RequestParam(required = false) String endTime) {
        return logisticsRequirementService.moduleRecords(module, limit, keyword, startTime, endTime);
    }
}
