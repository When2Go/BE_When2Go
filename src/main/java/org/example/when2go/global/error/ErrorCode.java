package org.example.when2go.global.error;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드 인터페이스
 * 각 도메인에서 이 인터페이스를 구현한 enum을 생성하여 도메인별 에러를 관리하면 됩니다
 */
public interface ErrorCode {

    HttpStatus getHttpStatus();

    String getCode();

    String getMessage();
}