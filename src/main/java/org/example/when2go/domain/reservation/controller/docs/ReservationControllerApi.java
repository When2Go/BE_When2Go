package org.example.when2go.domain.reservation.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.when2go.domain.reservation.dto.request.ReservationCreateRequest;
import org.example.when2go.domain.reservation.dto.response.ReservationCreateResponse;

@Tag(name = "예약", description = "예약(Reservation) API")
public interface ReservationControllerApi {

    @Operation(
            summary = "예약 생성",
            description = "요청 헤더 `X-Device-Id`로 식별되는 회원이 새 예약을 생성한다. "
                    + "출발지/도착지의 좌표와 이름, 경로 옵션, 도착 시각, 반복 요일 목록을 받아 저장한다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "예약 생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = org.example.when2go.global.response.ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "성공 응답",
                                    value = "{\"success\": true, \"code\": \"OK\", \"message\": \"요청이 성공했습니다.\", "
                                            + "\"data\": {\"reservationId\": 1}}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "유효하지 않은 요청 (X-Device-Id 누락/형식 오류, 필수 필드 누락, repeatDays 비어있음 등)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = org.example.when2go.global.response.ApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "X-Device-Id 헤더 누락",
                                            value = "{\"success\": false, \"code\": \"GLOBAL_003\", \"message\": \"필수 파라미터가 누락되었습니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "필수 필드 누락 또는 repeatDays 비어있음",
                                            value = "{\"success\": false, \"code\": \"GLOBAL_001\", \"message\": \"잘못된 입력값입니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "arrivalTime 형식 오류",
                                            value = "{\"success\": false, \"code\": \"GLOBAL_002\", \"message\": \"잘못된 타입입니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 deviceId의 회원이 존재하지 않음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = org.example.when2go.global.response.ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "사용자 없음",
                                    value = "{\"success\": false, \"code\": \"USER_001\", \"message\": \"사용자를 찾을 수 없습니다.\"}"
                            )
                    )
            )
    })
    org.example.when2go.global.response.ApiResponse<ReservationCreateResponse> create(
            @Parameter(
                    name = "X-Device-Id",
                    description = "회원 식별용 디바이스 ID (UUID 36자)",
                    in = ParameterIn.HEADER,
                    required = true
            )
            @NotBlank
            @Size(min = 36, max = 36)
            String deviceId,
            @Valid
            ReservationCreateRequest request
    );

    @Operation(
            summary = "예약 삭제",
            description = "요청 헤더 `X-Device-Id`로 식별되는 회원의 예약을 삭제한다. "
                    + "삭제 시 해당 예약을 참조하던 Trip의 `reservation_id`는 NULL로 끊고 Reservation은 hard delete 한다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "예약 삭제 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = org.example.when2go.global.response.ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "성공 응답",
                                    value = "{\"success\": true, \"code\": \"OK\", \"message\": \"요청이 성공했습니다.\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "유효하지 않은 요청 (X-Device-Id 누락/형식 오류, reservationId 타입 오류 등)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = org.example.when2go.global.response.ApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "X-Device-Id 헤더 누락",
                                            value = "{\"success\": false, \"code\": \"GLOBAL_003\", \"message\": \"필수 파라미터가 누락되었습니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "reservationId 타입 오류",
                                            value = "{\"success\": false, \"code\": \"GLOBAL_002\", \"message\": \"잘못된 타입입니다.\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "본인 소유 예약이 아님",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = org.example.when2go.global.response.ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "권한 없음",
                                    value = "{\"success\": false, \"code\": \"RESERVATION_002\", \"message\": \"예약에 대한 권한이 없습니다.\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "해당 deviceId의 회원이 존재하지 않거나 해당 id의 예약이 존재하지 않음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = org.example.when2go.global.response.ApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "사용자 없음",
                                            value = "{\"success\": false, \"code\": \"USER_001\", \"message\": \"사용자를 찾을 수 없습니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "예약 없음",
                                            value = "{\"success\": false, \"code\": \"RESERVATION_001\", \"message\": \"예약을 찾을 수 없습니다.\"}"
                                    )
                            }
                    )
            )
    })
    org.example.when2go.global.response.ApiResponse<Void> delete(
            @Parameter(
                    name = "X-Device-Id",
                    description = "회원 식별용 디바이스 ID (UUID 36자)",
                    in = ParameterIn.HEADER,
                    required = true
            )
            @NotBlank
            @Size(min = 36, max = 36)
            String deviceId,
            @Parameter(
                    name = "reservationId",
                    description = "삭제할 예약 ID",
                    in = ParameterIn.PATH,
                    required = true
            )
            Long reservationId
    );
}
