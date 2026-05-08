package org.example.when2go.domain.notification.listener;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.example.when2go.domain.notification.event.NotificationScheduleScanRequestedEvent;
import org.example.when2go.domain.notification.service.schedule.NotificationScheduleClaimService;
import org.example.when2go.domain.notification.service.schedule.NotificationScheduleOutboxCreationService;
import org.junit.jupiter.api.Test;

class NotificationScheduleScanEventListenerTest {

    private final NotificationScheduleClaimService notificationScheduleClaimService =
            org.mockito.Mockito.mock(NotificationScheduleClaimService.class);
    private final NotificationScheduleOutboxCreationService notificationScheduleOutboxCreationService =
            org.mockito.Mockito.mock(NotificationScheduleOutboxCreationService.class);
    private final NotificationScheduleScanEventListener listener =
            new NotificationScheduleScanEventListener(
                    notificationScheduleClaimService,
                    notificationScheduleOutboxCreationService
            );

    // scan 이벤트를 받으면 claim한 스케줄 id로 outbox 생성을 위임하는지 확인한다.
    @Test
    void handleClaimsSchedulesAndCreatesOutboxes() {
        when(notificationScheduleClaimService.claimDueSchedules(500)).thenReturn(List.of(1L, 2L));

        listener.handle(new NotificationScheduleScanRequestedEvent(500));

        verify(notificationScheduleOutboxCreationService).createOutboxes(List.of(1L, 2L));
    }

    // claim 결과가 없으면 outbox 생성 서비스를 호출하지 않는지 확인한다.
    @Test
    void handleDoesNothingWhenNoSchedulesClaimed() {
        when(notificationScheduleClaimService.claimDueSchedules(500)).thenReturn(List.of());

        listener.handle(new NotificationScheduleScanRequestedEvent(500));

        verify(notificationScheduleOutboxCreationService, never()).createOutboxes(org.mockito.ArgumentMatchers.any());
    }
}
