package jimmy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rabbitmq")
public class RabbitMqProperties {

    private String demoExchange = "practice.demo.exchange";
    private String demoQueue = "practice.demo.queue";
    private String demoRoutingKey = "practice.demo.routing-key";

    public String getDemoExchange() {
        return demoExchange;
    }

    public void setDemoExchange(String demoExchange) {
        this.demoExchange = demoExchange;
    }

    public String getDemoQueue() {
        return demoQueue;
    }

    public void setDemoQueue(String demoQueue) {
        this.demoQueue = demoQueue;
    }

    public String getDemoRoutingKey() {
        return demoRoutingKey;
    }

    public void setDemoRoutingKey(String demoRoutingKey) {
        this.demoRoutingKey = demoRoutingKey;
    }
}
