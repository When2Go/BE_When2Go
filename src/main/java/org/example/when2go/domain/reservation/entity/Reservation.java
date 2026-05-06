package org.example.when2go.domain.reservation.entity;

import jakarta.persistence.Column;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

    @Column(name = "repeat_days")
    private String repeatDays;

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
            String repeatDays,
            LocalDateTime reservationDate
    ) {
        this.user = user;
        this.nickname = nickname;
        this.originName = originName;
        this.originLat = originLat;
        this.originLng = originLng;
        this.destName = destName;
        this.destLat = destLat;
        this.destLng = destLng;
        this.routeOption = routeOption;
        this.arrivalTime = arrivalTime;
        this.reservationType = reservationType;
        this.repeatDays = repeatDays;
        this.reservationDate = reservationDate;
    }
}
