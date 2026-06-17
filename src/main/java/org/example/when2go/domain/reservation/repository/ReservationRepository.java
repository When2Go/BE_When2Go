package org.example.when2go.domain.reservation.repository;

import java.util.List;
import org.example.when2go.domain.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findAllByUserIdOrderByArrivalTimeAscIdAsc(Long userId);
}
