package org.example.when2go.domain.notification.service.schedule;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.example.when2go.global.config.notification.NotificationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

@Disabled("release 단순화로 비활성화 - outbox 흐름 제거됨")
class NotificationScheduleDrainServiceTest {

    private final NotificationScheduleBatchService notificationScheduleBatchService =
            org.mockito.Mockito.mock(NotificationScheduleBatchService.class);
    private final NotificationProperties notificationProperties = new NotificationProperties();
    private final NotificationScheduleDrainService drainService =
            new NotificationScheduleDrainService(
                    notificationScheduleBatchService,
                    notificationProperties
            );

    @BeforeEach
    void setUp() {
        notificationProperties.getSchedule().setMaxDrainSize(5);
    }

    @Test
    void drainStopsImmediatelyWhenFirstBatchIsEmpty() {
        when(notificationScheduleBatchService.processDueScheduleBatch(2)).thenReturn(0);

        drainService.drainDueSchedules(2);

        verify(notificationScheduleBatchService).processDueScheduleBatch(2);
        verifyNoMoreInteractions(notificationScheduleBatchService);
    }

    @Test
    void drainRepeatsBatchesUntilProcessedCountIsZero() {
        notificationProperties.getSchedule().setMaxDrainSize(6);
        when(notificationScheduleBatchService.processDueScheduleBatch(2))
                .thenReturn(2)
                .thenReturn(2)
                .thenReturn(0);

        drainService.drainDueSchedules(2);

        verify(notificationScheduleBatchService, times(3)).processDueScheduleBatch(2);
    }

    @Test
    void drainUsesRemainingCapacityAsLastBatchLimitAndStopsAtMaxDrainSize() {
        notificationProperties.getSchedule().setMaxDrainSize(3);
        when(notificationScheduleBatchService.processDueScheduleBatch(2)).thenReturn(2);
        when(notificationScheduleBatchService.processDueScheduleBatch(1)).thenReturn(1);

        drainService.drainDueSchedules(2);

        InOrder inOrder = inOrder(notificationScheduleBatchService);
        inOrder.verify(notificationScheduleBatchService).processDueScheduleBatch(2);
        inOrder.verify(notificationScheduleBatchService).processDueScheduleBatch(1);
        verifyNoMoreInteractions(notificationScheduleBatchService);
    }

    @Test
    void drainCountsClaimedIdsEvenWhenNothingIsCreated() {
        when(notificationScheduleBatchService.processDueScheduleBatch(2))
                .thenReturn(2)
                .thenReturn(0);

        drainService.drainDueSchedules(2);

        verify(notificationScheduleBatchService, times(2)).processDueScheduleBatch(2);
    }

    @Test
    void drainPropagatesBatchFailureWithoutRetry() {
        RuntimeException exception = new RuntimeException("temporary");
        when(notificationScheduleBatchService.processDueScheduleBatch(2)).thenThrow(exception);

        assertThatThrownBy(() -> drainService.drainDueSchedules(2))
                .isSameAs(exception);

        verify(notificationScheduleBatchService, times(1)).processDueScheduleBatch(2);
    }
}
