package org.example.when2go.domain.trip.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.when2go.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NavigationParseErrorCode implements ErrorCode {

    INVALID_AUDIO(HttpStatus.BAD_REQUEST, "PARSE_001", "유효하지 않은 오디오 파일입니다."),
    PARSE_FAILED(HttpStatus.BAD_GATEWAY, "PARSE_002", "음성 파싱에 실패했습니다."),
    PARSE_QUEUE_FULL(HttpStatus.SERVICE_UNAVAILABLE, "PARSE_003", "처리 대기열이 가득 찼습니다."),
    PARSE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "PARSE_004", "음성 파싱 처리 시간이 초과되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
