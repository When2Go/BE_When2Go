package org.example.when2go.domain.trip.dto;

public record NavigationParseResponse(
        // 출발지. 음성에 명시가 없으면 "현재 위치"
        String startLocation,

        // 도착지
        String endLocation,

        // 약속 일시 (yyyy-MM-dd HH:mm), 시간 정보가 없으면 null
        String appointmentTime
) {
}
