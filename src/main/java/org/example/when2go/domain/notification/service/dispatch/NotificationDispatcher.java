package org.example.when2go.domain.notification.service.dispatch;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.notification.client.NotificationPushClient;
import org.example.when2go.domain.notification.dto.NotificationMessage;
import org.example.when2go.domain.notification.dto.PushMessage;
import org.example.when2go.domain.notification.dto.PushSendResult;
import org.example.when2go.domain.notification.entity.NotificationSchedule;
import org.example.when2go.domain.notification.repository.NotificationScheduleRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationScheduleRepository notificationScheduleRepository;
    private final ObjectProvider<NotificationPushClient> notificationPushClientProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchOne(Long scheduleId, LocalDateTime expirationThreshold) {
        NotificationSchedule schedule = notificationScheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            return;
        }
        if (schedule.getStatus() != org.example.when2go.domain.notification.entity.NotificationScheduleStatus.PENDING) {
            return;
        }

        if (schedule.getScheduledAt().isBefore(expirationThreshold)) {
            schedule.markExpired();
            log.info(
                    "event=notification.expired scheduleId={} tripId={} type={} scheduledAt={}",
                    schedule.getId(),
                    schedule.getTrip().getId(),
                    schedule.getType(),
                    schedule.getScheduledAt()
            );
            return;
        }

        String token = schedule.getUser().getFcmToken();
        if (!StringUtils.hasText(token)) {
            schedule.markFailed();
            log.warn(
                    "event=notification.token_missing scheduleId={} userId={} tripId={}",
                    schedule.getId(),
                    schedule.getUser().getId(),
                    schedule.getTrip().getId()
            );
            return;
        }

        NotificationPushClient pushClient = notificationPushClientProvider.getIfAvailable();
        if (pushClient == null) {
            schedule.markFailed();
            log.warn(
                    "event=notification.push_client_unavailable scheduleId={}",
                    schedule.getId()
            );
            return;
        }

        NotificationMessage message = schedule.getType().toMessage();
        PushSendResult result = pushClient.send(new PushMessage(
                token,
                message.title(),
                message.body(),
                Map.of(
                        "tripId", String.valueOf(schedule.getTrip().getId()),
                        "type", schedule.getType().name()
                )
        ));

        if (result.success()) {
            schedule.markDone();
            log.info(
                    "event=notification.sent scheduleId={} type={} messageId={}",
                    schedule.getId(),
                    schedule.getType(),
                    result.messageId()
            );
        } else {
            schedule.markFailed();
            log.warn(
                    "event=notification.send_failed scheduleId={} type={} errorCode={} errorMessage={}",
                    schedule.getId(),
                    schedule.getType(),
                    result.errorCode(),
                    result.errorMessage()
            );
        }
    }
}
