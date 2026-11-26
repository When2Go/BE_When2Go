package org.example.when2go.global.config.notification;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@EnableAsync
@Configuration
public class NotificationAsyncConfig {

    public static final String SCHEDULE_PROCESSOR_EXECUTOR = "notificationScheduleProcessorExecutor";
    public static final String OUTBOX_PUBLISHER_EXECUTOR = "notificationOutboxPublisherExecutor";
    public static final String PUSH_EXECUTOR = "notificationPushExecutor";

    @Bean(name = SCHEDULE_PROCESSOR_EXECUTOR)
    ThreadPoolTaskExecutor notificationScheduleProcessorExecutor() {
        return executor(SCHEDULE_PROCESSOR_EXECUTOR, 1, 2, 100);
    }

    @Bean(name = OUTBOX_PUBLISHER_EXECUTOR)
    ThreadPoolTaskExecutor notificationOutboxPublisherExecutor() {
        return executor(OUTBOX_PUBLISHER_EXECUTOR, 1, 1, 100);
    }

    // FCM 발송은 대부분 블로킹 I/O 대기이므로 작업마다 가상 스레드를 생성한다.
    @Bean(name = PUSH_EXECUTOR)
    Executor notificationPushExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(PUSH_EXECUTOR + "-");
        executor.setVirtualThreads(true);
        return executor;
    }

    private ThreadPoolTaskExecutor executor(String name, int corePoolSize, int maxPoolSize, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(name + "-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setRejectedExecutionHandler((runnable, threadPoolExecutor) -> {
            int queueSize = queueSize(threadPoolExecutor);
            log.warn("event=notification.async_task_rejected executor={} queueSize={}", name, queueSize);
        });
        executor.initialize();
        return executor;
    }

    private int queueSize(ThreadPoolExecutor executor) {
        return executor.getQueue().size();
    }
}
