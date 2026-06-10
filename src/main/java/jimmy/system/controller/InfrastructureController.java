package jimmy.system.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import jimmy.common.model.ApiResponse;
import jimmy.system.model.InfrastructureStatus;
import jimmy.system.service.InfrastructureStatusService;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 基础设施巡检控制器 —— 运行时检测 Nacos/Sentinel/ES/Redis/RabbitMQ 连通性。
 */
@RestController
@RequestMapping("/infra")
public class InfrastructureController {

    private final InfrastructureStatusService infrastructureStatusService;

    public InfrastructureController(InfrastructureStatusService infrastructureStatusService) {
        this.infrastructureStatusService = infrastructureStatusService;
    }

    @GetMapping("/status")
    public ApiResponse<InfrastructureStatus> status() {
        return ApiResponse.success(infrastructureStatusService.status());
    }

    @GetMapping("/nacos/services")
    public ApiResponse<List<String>> services() {
        return ApiResponse.success(infrastructureStatusService.services());
    }

    @GetMapping("/nacos/instances")
    public ApiResponse<List<ServiceInstance>> instances(String serviceId) {
        return ApiResponse.success(infrastructureStatusService.instances(serviceId));
    }

    @GetMapping("/sentinel/ping")
    @SentinelResource(value = "infraSentinelPing", fallback = "sentinelFallback")
    public ApiResponse<InfrastructureStatus> sentinelPing() {
        return ApiResponse.success(infrastructureStatusService.sentinelPing());
    }

    @GetMapping("/elasticsearch/client")
    public ApiResponse<InfrastructureStatus> elasticsearchClient() {
        return ApiResponse.success(infrastructureStatusService.elasticsearchClient());
    }

    @GetMapping("/redis/client")
    public ApiResponse<InfrastructureStatus> redisClient() {
        return ApiResponse.success(infrastructureStatusService.redisClient());
    }

    @GetMapping("/rabbitmq/client")
    public ApiResponse<InfrastructureStatus> rabbitmqClient() {
        return ApiResponse.success(infrastructureStatusService.rabbitmqClient());
    }

    public ApiResponse<InfrastructureStatus> sentinelFallback() {
        return ApiResponse.success(infrastructureStatusService.sentinelFallback());
    }
}
