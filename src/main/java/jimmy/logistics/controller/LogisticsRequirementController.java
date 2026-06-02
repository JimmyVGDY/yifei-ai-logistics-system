package jimmy.logistics.controller;

import jimmy.logistics.model.LogisticsDashboardSummary;
import jimmy.logistics.model.ModuleQueryDTO;
import jimmy.logistics.model.ModuleRecordVO;
import jimmy.logistics.annotation.OperationLog;
import jimmy.logistics.service.LogisticsRequirementService;
import jimmy.model.ApiResponse;
import jimmy.model.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/logistics")
public class LogisticsRequirementController {

    private final LogisticsRequirementService logisticsRequirementService;

    public LogisticsRequirementController(LogisticsRequirementService logisticsRequirementService) {
        this.logisticsRequirementService = logisticsRequirementService;
    }

    @OperationLog("运营看板-查询统计")
    @GetMapping("/dashboard")
    public ApiResponse<LogisticsDashboardSummary> dashboard() {
        return ApiResponse.success(logisticsRequirementService.dashboardSummary());
    }

    @GetMapping("/modules/{module}")
    public ApiResponse<PageResult<ModuleRecordVO>> moduleRecords(@PathVariable String module,
                                                                 @RequestParam(defaultValue = "1") int page,
                                                                 @RequestParam(defaultValue = "20") int pageSize,
                                                                 @RequestParam(required = false) Integer limit,
                                                                 @RequestParam(required = false) String keyword,
                                                                 @RequestParam(required = false) String startTime,
                                                                 @RequestParam(required = false) String endTime,
                                                                 @RequestParam(required = false) String usage) {
        ModuleQueryDTO query = new ModuleQueryDTO();
        query.setPage(page);
        query.setPageSize(limit == null ? pageSize : limit);
        query.setKeyword(keyword);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setUsage(usage);
        return ApiResponse.success(logisticsRequirementService.modulePage(module, query));
    }
}
