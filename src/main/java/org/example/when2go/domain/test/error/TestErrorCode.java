package org.example.when2go.domain.test.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.when2go.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Test 도메인 에러코드
 */
@Getter
@RequiredArgsConstructor
public enum TestErrorCode implements ErrorCode {

    TEST_NOT_FOUND(HttpStatus.NOT_FOUND, "TEST_001", "테스트를 찾을 수 없습니다."),
    TEST_ALREADY_EXISTS(HttpStatus.CONFLICT, "TEST_002", "이미 존재하는 테스트입니다."),
    FCM_PUSH_CLIENT_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "TEST_003", "FCM 발송 클라이언트가 비활성화되어 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
