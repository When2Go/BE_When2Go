package org.example.when2go.global.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.example.when2go.domain.trip.event.TripRecalcScanRequestedEvent;
import org.example.when2go.global.config.trip.TripRecalcProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class TripRecalcSchedulerTest {

    private final ApplicationEventPublisher eventPublisher =
            org.mockito.Mockito.mock(ApplicationEventPublisher.class);
    private final TripRecalcProperties tripRecalcProperties = new TripRecalcProperties();
    private final TripRecalcScheduler tripRecalcScheduler =
            new TripRecalcScheduler(eventPublisher, tripRecalcProperties);

    // trip recalc scheduler가 직접 처리하지 않고 scan 요청 이벤트만 발행하는지 확인한다.
    @Test
    void triggerTripRecalcScanPublishesScanRequestedEvent() {
        tripRecalcProperties.setClaimSize(77);

        tripRecalcScheduler.triggerTripRecalcScan();

        ArgumentCaptor<TripRecalcScanRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(TripRecalcScanRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().limit()).isEqualTo(77);
    }
}
