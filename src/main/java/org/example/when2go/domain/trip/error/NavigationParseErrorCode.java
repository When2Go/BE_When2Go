package org.example.when2go.domain.trip.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.when2go.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NavigationParseErrorCode implements ErrorCode {

    INVALID_AUDIO(HttpStatus.BAD_REQUEST, "PARSE_001", "유효하지 않은 오디오 파일입니다."),
    PARSE_FAILED(HttpStatus.BAD_GATEWAY, "PARSE_002", "음성 파싱에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
