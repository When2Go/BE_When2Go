package org.example.when2go.domain.reservation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.when2go.domain.reservation.converter.DayOfWeekSetConverter;
import org.example.when2go.domain.reservation.enums.ReservationType;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.global.common.entity.BaseEntity;

@Getter
@Entity
@Table(name = "reservations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column
    private String nickname;

    @Column(name = "origin_name", nullable = false)
    private String originName;

    @Column(name = "origin_lat", nullable = false)
    private Double originLat;

    @Column(name = "origin_lng", nullable = false)
    private Double originLng;

    @Column(name = "dest_name", nullable = false)
    private String destName;

    @Column(name = "dest_lat", nullable = false)
    private Double destLat;

    @Column(name = "dest_lng", nullable = false)
    private Double destLng;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_option", nullable = false, length = 30)
    private RouteOption routeOption;

    @Column(name = "arrival_time", nullable = false)
    private LocalTime arrivalTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_type", nullable = false, length = 20)
    private ReservationType reservationType;

    @Convert(converter = DayOfWeekSetConverter.class)
    @Column(name = "repeat_days")
    private Set<DayOfWeek> repeatDays;

    @Column(name = "reservation_date")
    private LocalDateTime reservationDate;

    @Builder
    private Reservation(
            AppUser user,
            String nickname,
            String originName,
            Double originLat,
            Double originLng,
            String destName,
            Double destLat,
            Double destLng,
            RouteOption routeOption,
            LocalTime arrivalTime,
            ReservationType reservationType,
            Set<DayOfWeek> repeatDays,
            LocalDateTime reservationDate
    ) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.nickname = nickname;
        this.originName = Objects.requireNonNull(originName, "originName must not be null");
        this.originLat = Objects.requireNonNull(originLat, "originLat must not be null");
        this.originLng = Objects.requireNonNull(originLng, "originLng must not be null");
        this.destName = Objects.requireNonNull(destName, "destName must not be null");
        this.destLat = Objects.requireNonNull(destLat, "destLat must not be null");
        this.destLng = Objects.requireNonNull(destLng, "destLng must not be null");
        this.routeOption = Objects.requireNonNull(routeOption, "routeOption must not be null");
        this.arrivalTime = Objects.requireNonNull(arrivalTime, "arrivalTime must not be null");
        this.reservationType = Objects.requireNonNull(reservationType, "reservationType must not be null");
        validateScheduleFields(this.reservationType, repeatDays, reservationDate);
        this.repeatDays = repeatDays;
        this.reservationDate = reservationDate;
    }

    private void validateScheduleFields(
            ReservationType reservationType,
            Set<DayOfWeek> repeatDays,
            LocalDateTime reservationDate
    ) {
        if (reservationType == ReservationType.ONCE) {
            Objects.requireNonNull(reservationDate, "reservationDate must not be null");
            if (repeatDays != null && !repeatDays.isEmpty()) {
                throw new IllegalArgumentException("repeatDays must be empty for ONCE reservation");
            }
            return;
        }

        if (reservationType == ReservationType.REPEAT) {
            if (repeatDays == null || repeatDays.isEmpty()) {
                throw new NullPointerException("repeatDays must not be null");
            }
            if (reservationDate != null) {
                throw new IllegalArgumentException("reservationDate must be null for REPEAT reservation");
            }
        }
    }
}
