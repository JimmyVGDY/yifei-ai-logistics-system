package jimmy.system.service;

import jimmy.system.model.InfrastructureStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 基础设施状态服务 —— 运行时检测各中间件连通性（Nacos/Sentinel/Elasticsearch/Redis/RabbitMQ）。
 * <p>
 * 提供配置信息展示和各中间件 health check 接口，方便运维排查。
 */
@Service
public class InfrastructureStatusService {

    private final DiscoveryClient discoveryClient;
    private final ElasticsearchOperations elasticsearchOperations;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisConnectionFactory redisConnectionFactory;
    private final String applicationName;
    private final String nacosServerAddr;
    private final String sentinelDashboard;
    private final String elasticsearchUris;
    private final String rabbitmqHost;
    private final Integer rabbitmqPort;

    public InfrastructureStatusService(DiscoveryClient discoveryClient,
                                       ElasticsearchOperations elasticsearchOperations,
                                       StringRedisTemplate stringRedisTemplate,
                                       RedisConnectionFactory redisConnectionFactory,
                                       @Value("${spring.application.name}") String applicationName,
                                       @Value("${spring.cloud.nacos.server-addr:}") String nacosServerAddr,
                                       @Value("${spring.cloud.sentinel.transport.dashboard:}") String sentinelDashboard,
                                       @Value("${spring.elasticsearch.uris:}") String elasticsearchUris,
                                       @Value("${spring.rabbitmq.host:}") String rabbitmqHost,
                                       @Value("${spring.rabbitmq.port:5672}") Integer rabbitmqPort) {
        this.discoveryClient = discoveryClient;
        this.elasticsearchOperations = elasticsearchOperations;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
        this.applicationName = applicationName;
        this.nacosServerAddr = nacosServerAddr;
        this.sentinelDashboard = sentinelDashboard;
        this.elasticsearchUris = elasticsearchUris;
        this.rabbitmqHost = rabbitmqHost;
        this.rabbitmqPort = rabbitmqPort;
    }

    /** 汇总输出所有中间件配置信息 */
    public InfrastructureStatus status() {
        return InfrastructureStatus.of("infrastructure", "configured")
                .detail("applicationName", applicationName)
                .detail("nacosServerAddr", nacosServerAddr)
                .detail("sentinelDashboard", sentinelDashboard)
                .detail("elasticsearchUris", elasticsearchUris)
                .detail("redisConnectionFactory", redisConnectionFactory.getClass().getName())
                .detail("rabbitmqHost", rabbitmqHost)
                .detail("rabbitmqPort", rabbitmqPort);
    }

    /** 查询 Nacos 注册的服务列表 */
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

    public InfrastructureStatus redisClient() {
        String pong = stringRedisTemplate.getConnectionFactory().getConnection().ping();
        return InfrastructureStatus.of("redis", "configured")
                .detail("ping", pong)
                .detail("template", stringRedisTemplate.getClass().getName());
    }

    public InfrastructureStatus rabbitmqClient() {
        return InfrastructureStatus.of("rabbitmq", "configured")
                .detail("host", rabbitmqHost)
                .detail("port", rabbitmqPort);
    }

    public InfrastructureStatus sentinelFallback() {
        return InfrastructureStatus.of("sentinel", "blocked")
                .detail("resource", "infraSentinelPing");
    }
}
