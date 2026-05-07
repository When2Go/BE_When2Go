package org.example.when2go.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.when2go.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // NPE가 발생하면 필수 값 누락 공통 응답으로 변환되는지 확인한다.
    @Test
    void handleNullPointerExceptionReturnsRequiredValueMissing() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleNullPointerException(
                new NullPointerException("routeOption must not be null")
        );

        assertThat(response.getStatusCode()).isEqualTo(GlobalErrorCode.REQUIRED_VALUE_MISSING.getHttpStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo(GlobalErrorCode.REQUIRED_VALUE_MISSING.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo(GlobalErrorCode.REQUIRED_VALUE_MISSING.getMessage());
    }

    // 잘못된 인자 예외가 발생하면 잘못된 입력값 공통 응답으로 변환되는지 확인한다.
    @Test
    void handleIllegalArgumentExceptionReturnsInvalidInputValue() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException("reservationDate must be null for REPEAT reservation")
        );

        assertThat(response.getStatusCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE.getHttpStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE.getMessage());
    }
}
