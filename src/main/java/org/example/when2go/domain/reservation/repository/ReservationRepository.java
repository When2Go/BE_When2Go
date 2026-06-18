package org.example.when2go.domain.reservation.repository;

import java.util.List;
import org.example.when2go.domain.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // join fetch로 N+1 문제 해결
    @Query("SELECT r FROM Reservation r JOIN FETCH r.user")
    List<Reservation> findAllWithUser();

    List<Reservation> findAllByUserIdOrderByArrivalTimeAscIdAsc(Long userId);

}
