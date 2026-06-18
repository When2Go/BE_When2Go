package org.example.when2go.global.scheduler;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.notification.service.dispatch.NotificationDispatchService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationDispatchService notificationDispatchService;

    @Scheduled(cron = "0 * * * * *")
    public void dispatchDueSchedules() {
        notificationDispatchService.dispatchDueSchedules();
    }
}
