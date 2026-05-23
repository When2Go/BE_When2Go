package org.example.when2go.global.error;

import lombok.extern.slf4j.Slf4j;
import org.example.when2go.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // DomainException 처리
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException e) {
        log.warn("DomainException: {}", e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode));
    }

    // Validation 예외 처리 (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("Validation failed: {}", e.getMessage());
        return ResponseEntity
                .status(GlobalErrorCode.INVALID_INPUT_VALUE.getHttpStatus())
                .body(ApiResponse.error(GlobalErrorCode.INVALID_INPUT_VALUE));
    }

    // 메서드 파라미터 검증 예외 처리 (@Validated 컨트롤러의 @RequestHeader/@PathVariable 등)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        log.warn("Method parameter validation failed: {}", e.getMessage());
        return ResponseEntity
                .status(GlobalErrorCode.INVALID_INPUT_VALUE.getHttpStatus())
                .body(ApiResponse.error(GlobalErrorCode.INVALID_INPUT_VALUE));
    }

    // 필수 요청 헤더 누락 예외 처리
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestHeaderException(MissingRequestHeaderException e) {
        log.warn("Missing request header: {}", e.getMessage());
        return ResponseEntity
                .status(GlobalErrorCode.MISSING_REQUEST_PARAMETER.getHttpStatus())
                .body(ApiResponse.error(GlobalErrorCode.MISSING_REQUEST_PARAMETER));
    }

    // 타입 불일치 예외 처리
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.warn("Type mismatch: {}", e.getMessage());
        return ResponseEntity
                .status(GlobalErrorCode.INVALID_TYPE_VALUE.getHttpStatus())
                .body(ApiResponse.error(GlobalErrorCode.INVALID_TYPE_VALUE));
    }

    // 필수 파라미터 누락 예외 처리
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.warn("Missing parameter: {}", e.getMessage());
        return ResponseEntity
                .status(GlobalErrorCode.MISSING_REQUEST_PARAMETER.getHttpStatus())
                .body(ApiResponse.error(GlobalErrorCode.MISSING_REQUEST_PARAMETER));
    }

    // JSON 파싱 예외 처리
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("Invalid JSON: {}", e.getMessage());
        return ResponseEntity
                .status(GlobalErrorCode.INVALID_JSON.getHttpStatus())
                .body(ApiResponse.error(GlobalErrorCode.INVALID_JSON));
    }

    // 필수 값 누락 예외 처리
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ApiResponse<Void>> handleNullPointerException(NullPointerException e) {
        log.warn("Required value missing: {}", e.getMessage());
        return ResponseEntity
                .status(GlobalErrorCode.REQUIRED_VALUE_MISSING.getHttpStatus())
                .body(ApiResponse.error(GlobalErrorCode.REQUIRED_VALUE_MISSING));
    }

    // 잘못된 인자 예외 처리
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Invalid argument: {}", e.getMessage());
        return ResponseEntity
                .status(GlobalErrorCode.INVALID_INPUT_VALUE.getHttpStatus())
                .body(ApiResponse.error(GlobalErrorCode.INVALID_INPUT_VALUE));
    }

    // 지원하지 않는 HTTP 메서드 예외 처리
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("Method not allowed: {}", e.getMessage());
        return ResponseEntity
                .status(GlobalErrorCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(ApiResponse.error(GlobalErrorCode.METHOD_NOT_ALLOWED));
    }

    // 리소스를 찾을 수 없는 예외 처리
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("Resource not found: {}", e.getMessage());
        return ResponseEntity
                .status(GlobalErrorCode.RESOURCE_NOT_FOUND.getHttpStatus())
                .body(ApiResponse.error(GlobalErrorCode.RESOURCE_NOT_FOUND));
    }

    // 기타 모든 예외 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected error occurred: ", e);
        return ResponseEntity
                .status(GlobalErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.error(GlobalErrorCode.INTERNAL_SERVER_ERROR));
    }
}
