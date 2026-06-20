package org.example.when2go.global.config.trip;

import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
public class TripAsyncConfig {

    public static final String NEARBY_RECOMMEND_EXECUTOR = "nearbyRecommendExecutor";

    @Bean(name = NEARBY_RECOMMEND_EXECUTOR)
    ThreadPoolTaskExecutor nearbyRecommendExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(NEARBY_RECOMMEND_EXECUTOR + "-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setRejectedExecutionHandler((runnable, threadPoolExecutor) -> {
            int queueSize = queueSize(threadPoolExecutor);
            log.warn("event=trip.async_task_rejected executor={} queueSize={}",
                    NEARBY_RECOMMEND_EXECUTOR, queueSize);
        });
        executor.initialize();
        return executor;
    }

    private int queueSize(ThreadPoolExecutor executor) {
        return executor.getQueue().size();
    }
}
