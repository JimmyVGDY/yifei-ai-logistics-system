package jimmy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * AI 异步任务线程池配置。
 * <p>
 * 用于 SSE 流式响应场景：将耗时的 AI 模型调用放入后台线程，
 * 主线程通过 SSE 实时推送工具调用进度和最终结果。
 * 核心线程和最大线程都设为 4，避免线程数爆炸；队列容量 50，超出时 CallerRunsPolicy 兜底。
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AiAsyncConfig {

    @Bean(name = "aiChatExecutor")
    public Executor aiChatExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-chat-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
