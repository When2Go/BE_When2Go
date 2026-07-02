package org.example.when2go.domain.notification.service.outbox;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.example.when2go.domain.notification.entity.NotificationOutboxStatus;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.junit.jupiter.api.Test;

class NotificationOutboxStatusServiceTest {

    private final NotificationScheduleOutboxRepository repository = mock(NotificationScheduleOutboxRepository.class);
    private final LocalDateTime fixedTime = LocalDateTime.of(2026, 7, 3, 12, 0);
    private final Clock clock = Clock.fixed(
            fixedTime.atZone(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault()
    );
    private final NotificationOutboxStatusService statusService = new NotificationOutboxStatusService(repository, clock);

    @Test
    void markSentDelegatesToRepository() {
        statusService.markSent(1L);
        verify(repository).markSent(List.of(1L), NotificationOutboxStatus.SENT, fixedTime);
    }

    @Test
    void markRetryableFailureDelegatesToRepository() {
        statusService.markRetryableFailure(2L);
        verify(repository).markRetryableFailure(List.of(2L), NotificationOutboxStatus.PENDING);
    }

    @Test
    void markFailedDelegatesToRepository() {
        statusService.markFailed(3L);
        verify(repository).markFailed(List.of(3L), NotificationOutboxStatus.FAILED);
    }
}
