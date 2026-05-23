package org.example.when2go.domain.trip.listener;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.trip.event.TripRecalcScanRequestedEvent;
import org.example.when2go.domain.trip.service.recalc.TripRecalcClaimService;
import org.example.when2go.domain.trip.service.recalc.TripRecalcRouteSearchService;
import org.example.when2go.global.config.notification.NotificationAsyncConfig;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripRecalcScanListener {

    private final TripRecalcClaimService tripRecalcClaimService;
    private final TripRecalcRouteSearchService tripRecalcRouteSearchService;

    @Async(NotificationAsyncConfig.SCHEDULE_PROCESSOR_EXECUTOR)
    @EventListener
    public void handle(TripRecalcScanRequestedEvent event) {
        try {
            if (!tripRecalcRouteSearchService.isAvailable()) {
                log.debug("event=trip.recalc_scan_skipped reason=odsay_route_client_missing");
                return;
            }
            List<Long> tripIds = tripRecalcClaimService.claim(event.limit());
            for (Long tripId : tripIds) {
                tripRecalcRouteSearchService.process(tripId);
            }
        } catch (RuntimeException e) {
            log.warn("event=trip.recalc_scan_listener_failed limit={}", event.limit(), e);
        }
    }
}
