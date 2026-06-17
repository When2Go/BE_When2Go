package org.example.when2go.domain.reservation.dto.response;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.example.when2go.domain.reservation.entity.Reservation;
import org.example.when2go.domain.route.enums.RouteOption;

public record ReservationListResponse(
        List<Item> items
) {

    public static ReservationListResponse from(List<Reservation> reservations) {
        return new ReservationListResponse(
                reservations.stream()
                        .map(Item::from)
                        .toList()
        );
    }

    public record Item(
            Long id,
            String nickname,
            String originName,
            String destName,
            LocalTime arrivalTime,
            Set<DayOfWeek> repeatDays,
            RouteOption routeOption
    ) {

        public static Item from(Reservation reservation) {
            return new Item(
                    reservation.getId(),
                    reservation.getNickname(),
                    reservation.getOriginName(),
                    reservation.getDestName(),
                    reservation.getArrivalTime(),
                    reservation.getRepeatDays(),
                    reservation.getRouteOption()
            );
        }
    }
}
