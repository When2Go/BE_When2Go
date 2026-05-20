package org.example.when2go.domain.notification.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.notification.event.NotificationScheduleScanRequestedEvent;
import org.example.when2go.domain.notification.service.schedule.NotificationScheduleDrainService;
import org.example.when2go.global.config.notification.NotificationAsyncConfig;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduleScanEventListener {

    private final NotificationScheduleDrainService notificationScheduleDrainService;

    @Async(NotificationAsyncConfig.SCHEDULE_PROCESSOR_EXECUTOR)
    @EventListener
    public void handle(NotificationScheduleScanRequestedEvent event) {
        try {
            notificationScheduleDrainService.drainDueSchedules(event.limit());
        } catch (RuntimeException e) {
            log.warn("event=notification.schedule_scan_listener_failed limit={}", event.limit(), e);
        }
    }
}
