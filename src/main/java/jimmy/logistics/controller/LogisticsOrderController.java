package jimmy.logistics.controller;

import jimmy.logistics.annotation.OperationLog;
import jimmy.logistics.entity.LogisticsOrder;
import jimmy.logistics.model.CreateLogisticsOrderRequest;
import jimmy.logistics.service.LogisticsOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/logistics/orders")
public class LogisticsOrderController {

    private final LogisticsOrderService logisticsOrderService;

    public LogisticsOrderController(LogisticsOrderService logisticsOrderService) {
        this.logisticsOrderService = logisticsOrderService;
    }

    @OperationLog("创建物流订单")
    @PostMapping
    public LogisticsOrder create(@RequestBody CreateLogisticsOrderRequest request) {
        return logisticsOrderService.create(request);
    }

    @GetMapping("/{orderNo}")
    public LogisticsOrder findByOrderNo(@PathVariable String orderNo) {
        return logisticsOrderService.findByOrderNo(orderNo);
    }

    @GetMapping
    public List<LogisticsOrder> findRecent(@RequestParam(defaultValue = "20") int limit) {
        return logisticsOrderService.findRecent(limit);
    }
}
