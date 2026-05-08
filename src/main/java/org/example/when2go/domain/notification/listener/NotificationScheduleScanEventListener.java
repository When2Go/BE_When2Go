package org.example.when2go.domain.notification.listener;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.notification.event.NotificationScheduleScanRequestedEvent;
import org.example.when2go.domain.notification.service.schedule.NotificationScheduleClaimService;
import org.example.when2go.domain.notification.service.schedule.NotificationScheduleOutboxCreationService;
import org.example.when2go.global.config.notification.NotificationAsyncConfig;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduleScanEventListener {

    private final NotificationScheduleClaimService notificationScheduleClaimService;
    private final NotificationScheduleOutboxCreationService notificationScheduleOutboxCreationService;

    @Async(NotificationAsyncConfig.SCHEDULE_PROCESSOR_EXECUTOR)
    @EventListener
    public void handle(NotificationScheduleScanRequestedEvent event) {
        try {
            List<Long> scheduleIds = notificationScheduleClaimService.claimDueSchedules(event.limit());
            if (scheduleIds.isEmpty()) {
                return;
            }
            notificationScheduleOutboxCreationService.createOutboxes(scheduleIds);
        } catch (RuntimeException e) {
            log.warn("event=notification.schedule_scan_listener_failed limit={}", event.limit(), e);
        }
    }
}
