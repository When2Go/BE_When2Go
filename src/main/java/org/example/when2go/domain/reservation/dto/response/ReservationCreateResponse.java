package org.example.when2go.domain.reservation.dto.response;

import org.example.when2go.domain.reservation.entity.Reservation;

public record ReservationCreateResponse(
        Long reservationId
) {

    public static ReservationCreateResponse from(Reservation reservation) {
        return new ReservationCreateResponse(reservation.getId());
    }
}
