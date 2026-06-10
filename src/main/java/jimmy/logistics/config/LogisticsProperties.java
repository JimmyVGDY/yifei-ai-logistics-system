package jimmy.logistics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 物流模块配置属性 —— 绑定 {@code app.logistics} 前缀，包含缓存 TTL 和 MQ 子配置。
 */
@ConfigurationProperties(prefix = "app.logistics")
public class LogisticsProperties {

    private String cachePrefix = "logistics";
    private long orderCacheTtlSeconds = 1800L;
    private Mq mq = new Mq();

    public String getCachePrefix() {
        return cachePrefix;
    }

    public void setCachePrefix(String cachePrefix) {
        this.cachePrefix = cachePrefix;
    }

    public long getOrderCacheTtlSeconds() {
        return orderCacheTtlSeconds;
    }

    public void setOrderCacheTtlSeconds(long orderCacheTtlSeconds) {
        this.orderCacheTtlSeconds = orderCacheTtlSeconds;
    }

    public Mq getMq() {
        return mq;
    }

    public void setMq(Mq mq) {
        this.mq = mq;
    }

    public static class Mq {
        private String orderExchange = "logistics.order.exchange";
        private String orderCreatedQueue = "logistics.order.created.queue";
        private String orderCreatedRoutingKey = "logistics.order.created";

        public String getOrderExchange() {
            return orderExchange;
        }

        public void setOrderExchange(String orderExchange) {
            this.orderExchange = orderExchange;
        }

        public String getOrderCreatedQueue() {
            return orderCreatedQueue;
        }

        public void setOrderCreatedQueue(String orderCreatedQueue) {
            this.orderCreatedQueue = orderCreatedQueue;
        }

        public String getOrderCreatedRoutingKey() {
            return orderCreatedRoutingKey;
        }

        public void setOrderCreatedRoutingKey(String orderCreatedRoutingKey) {
            this.orderCreatedRoutingKey = orderCreatedRoutingKey;
        }
    }
}
