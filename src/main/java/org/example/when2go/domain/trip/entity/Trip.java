package org.example.when2go.domain.trip.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.when2go.domain.reservation.entity.Reservation;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.global.common.entity.BaseEntity;

@Getter
@Entity
@Table(
        name = "trips",
        indexes = {
                @Index(name = "idx_trip_recalc", columnList = "recalc_phase, next_recalc_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Column(name = "origin_name", nullable = false)
    private String originName;

    @Column(name = "dest_name", nullable = false)
    private String destName;

    @Column(name = "origin_lat", nullable = false)
    private Double originLat;

    @Column(name = "origin_lng", nullable = false)
    private Double originLng;

    @Column(name = "dest_lat", nullable = false)
    private Double destLat;

    @Column(name = "dest_lng", nullable = false)
    private Double destLng;

    @Column(name = "arrival_time", nullable = false)
    private LocalDateTime arrivalTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_option", nullable = false, length = 30)
    private RouteOption routeOption;

    // TODO 사용자의 버퍼를 기본으로 넣기
    @Column(name = "buffer_minutes", nullable = false)
    private Integer bufferMinutes;

    @Column(name = "final_departure_time")
    private LocalDateTime finalDepartureTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "recalc_phase", nullable = false, length = 30)
    private TripRecalcPhase recalcPhase = TripRecalcPhase.INITIAL;

    @Column(name = "next_recalc_at")
    private LocalDateTime nextRecalcAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TripStatus status = TripStatus.PENDING;

    @Column(name = "actual_departed_at")
    private LocalDateTime actualDepartedAt;

    @Column(name = "actual_arrived_at")
    private LocalDateTime actualArrivedAt;

    @Column(name = "actual_minutes")
    private Integer actualMinutes;

    @Column(name = "error_minutes")
    private Integer errorMinutes;

    @Builder
    private Trip(
            AppUser user,
            Reservation reservation,
            String originName,
            String destName,
            Double originLat,
            Double originLng,
            Double destLat,
            Double destLng,
            LocalDateTime arrivalTime,
            RouteOption routeOption,
            Integer bufferMinutes,
            LocalDateTime finalDepartureTime,
            TripRecalcPhase recalcPhase,
            LocalDateTime nextRecalcAt,
            TripStatus status,
            LocalDateTime actualDepartedAt,
            LocalDateTime actualArrivedAt,
            Integer actualMinutes,
            Integer errorMinutes
    ) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.reservation = reservation;
        this.originName = Objects.requireNonNull(originName, "originName must not be null");
        this.destName = Objects.requireNonNull(destName, "destName must not be null");
        this.originLat = Objects.requireNonNull(originLat, "originLat must not be null");
        this.originLng = Objects.requireNonNull(originLng, "originLng must not be null");
        this.destLat = Objects.requireNonNull(destLat, "destLat must not be null");
        this.destLng = Objects.requireNonNull(destLng, "destLng must not be null");
        this.arrivalTime = Objects.requireNonNull(arrivalTime, "arrivalTime must not be null");
        this.routeOption = Objects.requireNonNull(routeOption, "routeOption must not be null");
        this.bufferMinutes = Objects.requireNonNull(bufferMinutes, "bufferMinutes must not be null");
        this.finalDepartureTime = finalDepartureTime;
        this.recalcPhase = recalcPhase == null ? TripRecalcPhase.INITIAL : recalcPhase;
        this.nextRecalcAt = Objects.requireNonNull(nextRecalcAt, "nextRecalcAt must not be null");
        this.status = status == null ? TripStatus.PENDING : status;
        this.actualDepartedAt = actualDepartedAt;
        this.actualArrivedAt = actualArrivedAt;
        this.actualMinutes = actualMinutes;
        this.errorMinutes = errorMinutes;
    }

    public void applyRecalcResult(TripRecalcPhase recalcPhase, LocalDateTime nextRecalcAt) {
        this.recalcPhase = Objects.requireNonNull(recalcPhase, "recalcPhase must not be null");
        this.nextRecalcAt = Objects.requireNonNull(nextRecalcAt, "nextRecalcAt must not be null");
    }

    public void holdRecalcUntil(LocalDateTime nextRecalcAt) {
        this.nextRecalcAt = Objects.requireNonNull(nextRecalcAt, "nextRecalcAt must not be null");
    }

    public void markFinalized(LocalDateTime finalDepartureTime) {
        this.finalDepartureTime = Objects.requireNonNull(
                finalDepartureTime,
                "finalDepartureTime must not be null"
        );
        this.recalcPhase = TripRecalcPhase.DONE;
        this.nextRecalcAt = null;
        this.status = TripStatus.SCHEDULED;
    }
}
