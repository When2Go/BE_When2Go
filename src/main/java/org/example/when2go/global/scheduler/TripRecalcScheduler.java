package org.example.when2go.global.scheduler;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.trip.event.TripRecalcScanRequestedEvent;
import org.example.when2go.global.config.trip.TripRecalcProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TripRecalcScheduler {

    private final ApplicationEventPublisher eventPublisher;
    private final TripRecalcProperties tripRecalcProperties;

    @Scheduled(fixedDelayString = "${trip.recalc.fixed-delay-millis:60000}")
    public void triggerTripRecalcScan() {
        eventPublisher.publishEvent(new TripRecalcScanRequestedEvent(
                tripRecalcProperties.getClaimSize()
        ));
    }
}
