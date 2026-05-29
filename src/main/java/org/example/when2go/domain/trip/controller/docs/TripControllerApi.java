package org.example.when2go.domain.trip.controller.docs;

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
import org.example.when2go.domain.trip.dto.TripCreateRequest;
import org.example.when2go.domain.trip.dto.TripCreateResponse;
import org.example.when2go.domain.trip.dto.TripDetailResponse;
import org.example.when2go.domain.trip.dto.TripListResponse;
import org.example.when2go.domain.trip.entity.TripStatus;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "여정", description = "여정(Trip) API")
public interface TripControllerApi {

    @Operation(
            summary = "여정 생성",
            description = "요청 헤더 `X-Device-Id`로 식별되는 회원이 새 여정을 생성한다. "
                    + "출발지/도착지의 좌표와 이름, 도착 희망 시각, 버퍼 시간(분), "
                    + "예상 소요 시간(초)을 받아 PENDING 상태로 저장한다. "
                    + "내부적으로 routeOption은 TRANSIT으로 고정되며, "
                    + "다음 재계산 시각(`nextRecalcAt`)은 도착 시각에서 소요 시간과 버퍼를 뺀 시각의 60분 전으로 설정된다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "여정 생성 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = org.example.when2go.global.response.ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "성공 응답",
                                    value = "{\"success\": true, \"code\": \"OK\", \"message\": \"요청이 성공했습니다.\", "
                                            + "\"data\": {\"tripId\": 1, \"status\": \"PENDING\", "
                                            + "\"originName\": \"집\", \"destName\": \"회사\", "
                                            + "\"arrivalTime\": \"2026-05-26T09:00:00\", \"bufferMinutes\": 10, "
                                            + "\"createdAt\": \"2026-05-26T07:30:00\"}}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "유효하지 않은 요청 (X-Device-Id 누락/형식 오류, 필수 필드 누락, 음수 값 등)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = org.example.when2go.global.response.ApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "X-Device-Id 헤더 누락",
                                            value = "{\"success\": false, \"code\": \"GLOBAL_003\", \"message\": \"필수 파라미터가 누락되었습니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "X-Device-Id 형식 오류",
                                            value = "{\"success\": false, \"code\": \"GLOBAL_001\", \"message\": \"잘못된 입력값입니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "필수 필드 누락 (originName 등)",
                                            value = "{\"success\": false, \"code\": \"GLOBAL_001\", \"message\": \"잘못된 입력값입니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "bufferMinutes 또는 durationSeconds 음수",
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
    org.example.when2go.global.response.ApiResponse<TripCreateResponse> create(
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
            TripCreateRequest request
    );

    @Operation(summary = "여정 목록 조회", description = "날짜와 상태로 필터링된 여정 목록을 반환한다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "status 값 오류 또는 date 형식 오류"
            )
    })
    org.example.when2go.global.response.ApiResponse<List<TripListResponse>> list(
            @Parameter(
                    name = "X-Device-Id",
                    in = ParameterIn.HEADER,
                    required = true)
            @NotBlank
            @Size(min = 36, max = 36)
            String deviceId,
            @Parameter(
                    description = "PENDING | SCHEDULED | COMPLETED",
                    required = true)
            TripStatus status,
            @Parameter(
                    description = "날짜 (yyyy-MM-dd)",
                    required = true)
            LocalDate date
    );

    @Operation(summary = "여정 상세 조회", description = "tripId에 해당하는 여정의 상세 정보를 반환한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "해당 tripId 없음 또는 타인 소유")
    })
    org.example.when2go.global.response.ApiResponse<TripDetailResponse> getDetail(
        @Parameter(
                name = "X-Device-Id",
                in = ParameterIn.HEADER,
                required = true)
        @NotBlank
        @Size(min = 36, max = 36)
        String deviceId,
        @Parameter(description = "Trip ID", required = true)
        Long tripId
    );

    @Operation(summary = "여정 삭제", description = "tripId에 해당하는 여정을 삭제한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "해당 tripId 없음 또는 타인 소유")
    })
    org.example.when2go.global.response.ApiResponse<Void> delete(
            @Parameter(
                    name = "X-Device-Id",
                    in = ParameterIn.HEADER,
                    required = true)
            @NotBlank
            @Size(min = 36, max = 36)
            String deviceId,
            @Parameter(
                    description = "Trip ID",
                    required = true)
            Long tripId
    );
}
