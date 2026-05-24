package org.example.when2go.domain.trip.entity;

public enum TripStatus {
    PENDING,        // Trip 생성
    SCHEDULED,      // 최종 출발시간 확정
    IN_PROGRESS,    // 사용자 출발
    COMPLETED,      // 도착 완료
    CANCELED        // 사용자가 취소함
}
