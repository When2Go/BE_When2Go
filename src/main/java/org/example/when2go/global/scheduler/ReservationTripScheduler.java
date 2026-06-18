package org.example.when2go.global.scheduler;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.reservation.service.ReservationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationTripScheduler {

    private final ReservationService reservationService;

    // 매일 자정에 오늘 요일 해당 반복 예약
    @Scheduled(cron = "${reservation.trip.cron:0 0 0 * * *}")
    public void createDailyTrips() {
        reservationService.createTodayTrips();
    }
}
