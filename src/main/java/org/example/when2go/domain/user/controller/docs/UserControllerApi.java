package org.example.when2go.domain.user.controller.docs;

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
import org.example.when2go.domain.user.dto.BufferMinutesUpdateRequest;
import org.example.when2go.domain.user.dto.BufferMinutesUpdateResponse;
import org.example.when2go.domain.user.dto.FcmTokenUpdateRequest;
import org.example.when2go.domain.user.dto.FcmTokenUpdateResponse;
import org.example.when2go.domain.user.dto.UserStatusResponse;
import org.example.when2go.domain.user.dto.UserRegisterRequest;
import org.example.when2go.domain.user.dto.UserResponse;

@Tag(name = "회원", description = "회원 API")
public interface UserControllerApi {

    @Operation(
            summary = "회원 등록 / 조회",
            description = "deviceId 기준으로 회원을 등록하거나 기존 회원을 조회한다. "
                    + "이미 동일 deviceId의 회원이 존재하면 기존 회원 정보를 그대로 반환한다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "회원 등록 또는 조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "유효하지 않은 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = org.example.when2go.global.response.ApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "deviceId 누락",
                                            value = "{\"success\": false, \"code\": \"GLOBAL_001\", \"message\": \"잘못된 입력값입니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "deviceId 형식 오류 (36자 아님)",
                                            value = "{\"success\": false, \"code\": \"GLOBAL_001\", \"message\": \"잘못된 입력값입니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "fcmToken 누락",
                                            value = "{\"success\": false, \"code\": \"GLOBAL_001\", \"message\": \"잘못된 입력값입니다.\"}"
                                    )
                            }
                    )
            )
    })
    org.example.when2go.global.response.ApiResponse<UserResponse> register(
            @Valid UserRegisterRequest request
    );

    @Operation(
            summary = "회원 등록 여부 확인",
            description = "요청 헤더 `X-Device-Id`에 담긴 deviceId의 회원이 등록되어 있는지 여부를 반환한다. "
                    + "앱 시작 시 회원 가입 여부를 가볍게 확인하기 위한 용도이며, "
                    + "응답의 `exists`가 false이면 클라이언트는 회원 등록(`POST /api/users`) 흐름으로 진입한다. "
                    + "헤더가 없거나 형식이 잘못된 경우 400을 반환한다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "확인 성공 — 본문의 `exists` 필드로 존재 여부 판별",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = org.example.when2go.global.response.ApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "등록된 회원",
                                            value = "{\"success\": true, \"code\": \"OK\", \"message\": \"요청이 성공했습니다.\", \"data\": {\"exists\": true}}"
                                    ),
                                    @ExampleObject(
                                            name = "미등록 회원",
                                            value = "{\"success\": true, \"code\": \"OK\", \"message\": \"요청이 성공했습니다.\", \"data\": {\"exists\": false}}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "유효하지 않은 요청 (X-Device-Id 누락 또는 형식 오류)",
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
                                    )
                            }
                    )
            )
    })
    org.example.when2go.global.response.ApiResponse<UserStatusResponse> exists(
            @Parameter(
                    name = "X-Device-Id",
                    description = "회원 식별용 디바이스 ID (UUID 36자)",
                    in = ParameterIn.HEADER,
                    required = true
            )
            @NotBlank
            @Size(min = 36, max = 36)
            String deviceId
    );

    @Operation(
            summary = "FCM 토큰 갱신",
            description = "요청 헤더 `X-Device-Id`에 담긴 deviceId의 회원에 대해 FCM 토큰을 새 값으로 갱신한다. "
                    + "동일한 토큰을 다시 전송해도 200 OK가 반환되며 응답 본문도 동일하다 (멱등)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "토큰 갱신 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = org.example.when2go.global.response.ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "성공 응답",
                                    value = "{\"success\": true, \"code\": \"OK\", \"message\": \"요청이 성공했습니다.\", "
                                            + "\"data\": {\"userId\": 1, \"deviceId\": \"550e8400-e29b-41d4-a716-446655440000\", "
                                            + "\"fcmToken\": \"fGx8aQ...:APA91b...\"}}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "유효하지 않은 요청 (X-Device-Id 누락/형식 오류, fcmToken 누락 또는 512자 초과)",
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
                                            name = "fcmToken 누락",
                                            value = "{\"success\": false, \"code\": \"GLOBAL_001\", \"message\": \"잘못된 입력값입니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "fcmToken 길이 초과",
                                            value = "{\"success\": false, \"code\": \"GLOBAL_001\", \"message\": \"잘못된 입력값입니다.\"}"
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
    org.example.when2go.global.response.ApiResponse<FcmTokenUpdateResponse> updateFcmToken(
            @Parameter(
                    name = "X-Device-Id",
                    description = "회원 식별용 디바이스 ID (UUID 36자)",
                    in = ParameterIn.HEADER,
                    required = true
            )
            @NotBlank
            @Size(min = 36, max = 36)
            String deviceId,
            @Valid FcmTokenUpdateRequest request
    );

    @Operation(
            summary = "버퍼 시간(분) 수정",
            description = "요청 헤더 `X-Device-Id`에 담긴 deviceId의 회원에 대해 bufferMinutes 값을 갱신한다. "
                    + "동일한 값을 다시 전송해도 200 OK가 반환된다 (멱등)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "버퍼 시간 갱신 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = org.example.when2go.global.response.ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "성공 응답",
                                    value = "{\"success\": true, \"code\": \"OK\", \"message\": \"요청이 성공했습니다.\", "
                                            + "\"data\": {\"userId\": 1, \"deviceId\": \"550e8400-e29b-41d4-a716-446655440000\", "
                                            + "\"bufferMinutes\": 15}}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "유효하지 않은 요청 (X-Device-Id 누락/형식 오류, bufferMinutes 누락 또는 음수)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = org.example.when2go.global.response.ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "잘못된 입력",
                                    value = "{\"success\": false, \"code\": \"GLOBAL_001\", \"message\": \"잘못된 입력값입니다.\"}"
                            )
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
    org.example.when2go.global.response.ApiResponse<BufferMinutesUpdateResponse> updateBufferMinutes(
            @Parameter(
                    name = "X-Device-Id",
                    description = "회원 식별용 디바이스 ID (UUID 36자)",
                    in = ParameterIn.HEADER,
                    required = true
            )
            @NotBlank
            @Size(min = 36, max = 36)
            String deviceId,
            @Valid BufferMinutesUpdateRequest request
    );
}
