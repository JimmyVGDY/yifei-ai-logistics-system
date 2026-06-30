package jimmy.logistics.controller;

import jimmy.logistics.annotation.OperationLog;
import jimmy.logistics.model.CreateLogisticsOrderRequest;
import jimmy.logistics.model.LogisticsOrderVO;
import jimmy.logistics.model.OrderSearchQueryDTO;
import jimmy.logistics.service.LogisticsOrderService;
import jimmy.logistics.service.LogisticsOrderSearchService;
import jimmy.common.model.ApiResponse;
import jimmy.common.model.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;

/**
 * 物流订单控制器 —— 订单创建、详情查询、列表查询、ES 搜索。
 */
@RestController
@RequestMapping("/logistics/orders")
public class LogisticsOrderController {

    private final LogisticsOrderService logisticsOrderService;
    private final LogisticsOrderSearchService logisticsOrderSearchService;

    public LogisticsOrderController(LogisticsOrderService logisticsOrderService,
                                    LogisticsOrderSearchService logisticsOrderSearchService) {
        this.logisticsOrderService = logisticsOrderService;
        this.logisticsOrderSearchService = logisticsOrderSearchService;
    }

    @OperationLog("创建物流订单")
    @PostMapping
    public ApiResponse<LogisticsOrderVO> create(@Valid @RequestBody CreateLogisticsOrderRequest request,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank() && (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank())) {
            request.setIdempotencyKey(idempotencyKey);
        }
        return ApiResponse.success(LogisticsOrderVO.from(logisticsOrderService.create(request)));
    }

    @OperationLog("运单管理-查看订单详情")
    @GetMapping("/{orderNo}")
    public ApiResponse<LogisticsOrderVO> findByOrderNo(@PathVariable String orderNo) {
        return ApiResponse.success(LogisticsOrderVO.from(logisticsOrderService.findByOrderNo(orderNo)));
    }

    @OperationLog("运单管理-查询近期订单")
    @GetMapping
    public ApiResponse<List<LogisticsOrderVO>> findRecent(@RequestParam(defaultValue = "20") int limit) {
        List<LogisticsOrderVO> records = new ArrayList<>();
        logisticsOrderService.findRecent(limit).forEach(order -> records.add(LogisticsOrderVO.from(order)));
        return ApiResponse.success(records);
    }

    @OperationLog("运单管理-搜索订单")
    @GetMapping("/search")
    public ApiResponse<PageResult<LogisticsOrderVO>> search(@Valid OrderSearchQueryDTO query) {
        return ApiResponse.success(logisticsOrderSearchService.search(query));
    }
}
