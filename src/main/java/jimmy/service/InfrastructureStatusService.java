package jimmy.service;

import jimmy.model.InfrastructureStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InfrastructureStatusService {

    private final DiscoveryClient discoveryClient;
    private final ElasticsearchOperations elasticsearchOperations;
    private final String applicationName;
    private final String nacosServerAddr;
    private final String sentinelDashboard;
    private final String elasticsearchUris;

    public InfrastructureStatusService(DiscoveryClient discoveryClient,
                                       ElasticsearchOperations elasticsearchOperations,
                                       @Value("${spring.application.name}") String applicationName,
                                       @Value("${spring.cloud.nacos.server-addr:}") String nacosServerAddr,
                                       @Value("${spring.cloud.sentinel.transport.dashboard:}") String sentinelDashboard,
                                       @Value("${spring.elasticsearch.uris:}") String elasticsearchUris) {
        this.discoveryClient = discoveryClient;
        this.elasticsearchOperations = elasticsearchOperations;
        this.applicationName = applicationName;
        this.nacosServerAddr = nacosServerAddr;
        this.sentinelDashboard = sentinelDashboard;
        this.elasticsearchUris = elasticsearchUris;
    }

    public InfrastructureStatus status() {
        return InfrastructureStatus.of("infrastructure", "configured")
                .detail("applicationName", applicationName)
                .detail("nacosServerAddr", nacosServerAddr)
                .detail("sentinelDashboard", sentinelDashboard)
                .detail("elasticsearchUris", elasticsearchUris);
    }

    public List<String> services() {
        return discoveryClient.getServices();
    }

    public List<ServiceInstance> instances(String serviceId) {
        return discoveryClient.getInstances(serviceId);
    }

    public InfrastructureStatus sentinelPing() {
        return InfrastructureStatus.of("sentinel", "ok")
                .detail("resource", "infraSentinelPing");
    }

    public InfrastructureStatus elasticsearchClient() {
        return InfrastructureStatus.of("elasticsearch", "configured")
                .detail("operations", elasticsearchOperations.getClass().getName());
    }

    public InfrastructureStatus sentinelFallback() {
        return InfrastructureStatus.of("sentinel", "blocked")
                .detail("resource", "infraSentinelPing");
    }
}
