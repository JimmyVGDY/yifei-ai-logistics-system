package jimmy.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import jimmy.model.InfrastructureStatus;
import jimmy.service.InfrastructureStatusService;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/infra")
public class InfrastructureController {

    private final InfrastructureStatusService infrastructureStatusService;

    public InfrastructureController(InfrastructureStatusService infrastructureStatusService) {
        this.infrastructureStatusService = infrastructureStatusService;
    }

    @GetMapping("/status")
    public InfrastructureStatus status() {
        return infrastructureStatusService.status();
    }

    @GetMapping("/nacos/services")
    public List<String> services() {
        return infrastructureStatusService.services();
    }

    @GetMapping("/nacos/instances")
    public List<ServiceInstance> instances(String serviceId) {
        return infrastructureStatusService.instances(serviceId);
    }

    @GetMapping("/sentinel/ping")
    @SentinelResource(value = "infraSentinelPing", fallback = "sentinelFallback")
    public InfrastructureStatus sentinelPing() {
        return infrastructureStatusService.sentinelPing();
    }

    @GetMapping("/elasticsearch/client")
    public InfrastructureStatus elasticsearchClient() {
        return infrastructureStatusService.elasticsearchClient();
    }

    public InfrastructureStatus sentinelFallback() {
        return infrastructureStatusService.sentinelFallback();
    }
}
