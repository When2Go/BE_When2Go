package org.example.when2go.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import org.example.when2go.domain.notification.entity.NotificationOutboxStatus;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.example.when2go.domain.notification.repository.NotificationScheduleRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@Disabled("release 단순화로 비활성화 - outbox 흐름 제거됨")
class NotificationCleanupServiceTest {

    private final NotificationScheduleOutboxRepository notificationScheduleOutboxRepository =
            org.mockito.Mockito.mock(NotificationScheduleOutboxRepository.class);
    private final NotificationScheduleRepository notificationScheduleRepository =
            org.mockito.Mockito.mock(NotificationScheduleRepository.class);
    private final NotificationCleanupService notificationCleanupService =
            new NotificationCleanupService(
                    notificationScheduleOutboxRepository,
                    notificationScheduleRepository
            );

    @SuppressWarnings("unchecked")
    @Test
    void deleteByTripIdExcludesProcessingOutboxes() {
        when(notificationScheduleOutboxRepository.deleteByTripIdAndStatusIn(eq(1L), anyCollection()))
                .thenReturn(2);
        when(notificationScheduleRepository.deleteByTripIdWithoutOutboxes(1L))
                .thenReturn(3);
        ArgumentCaptor<Collection<NotificationOutboxStatus>> statusesCaptor =
                ArgumentCaptor.forClass(Collection.class);

        int deletedCount = notificationCleanupService.deleteByTripId(1L);

        assertThat(deletedCount).isEqualTo(5);
        verify(notificationScheduleOutboxRepository).deleteByTripIdAndStatusIn(eq(1L), statusesCaptor.capture());
        assertThat(statusesCaptor.getValue())
                .containsExactly(
                        NotificationOutboxStatus.PENDING,
                        NotificationOutboxStatus.PUBLISHED,
                        NotificationOutboxStatus.FAILED
                )
                .doesNotContain(NotificationOutboxStatus.PROCESSING);
        verify(notificationScheduleRepository).deleteByTripIdWithoutOutboxes(1L);
    }
}
