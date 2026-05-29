package org.example.when2go.domain.trip.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.when2go.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TripErrorCode implements ErrorCode {

    TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_001", "여정을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
