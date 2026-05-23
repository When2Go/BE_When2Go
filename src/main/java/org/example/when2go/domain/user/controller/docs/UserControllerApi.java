package org.example.when2go.domain.user.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.when2go.domain.user.dto.FcmTokenUpdateRequest;
import org.example.when2go.domain.user.dto.FcmTokenUpdateResponse;
import org.example.when2go.domain.user.dto.UserRegisterRequest;
import org.example.when2go.domain.user.dto.UserResponse;
import org.springframework.web.bind.annotation.RequestBody;

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
            @Valid @RequestBody UserRegisterRequest request
    );

    @Operation(
            summary = "FCM 토큰 갱신",
            description = "지정한 deviceId의 회원에 대해 FCM 토큰을 새 값으로 갱신한다. "
                    + "동일한 토큰을 다시 전송해도 200 OK가 반환되며 응답 본문도 동일하다 (멱등)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 갱신 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "유효하지 않은 요청 (fcmToken 누락 또는 512자 초과)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = org.example.when2go.global.response.ApiResponse.class),
                            examples = {
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
            @Parameter(description = "회원 식별용 디바이스 ID (UUID 36자)", required = true)
            String deviceId,
            @Valid @RequestBody FcmTokenUpdateRequest request
    );
}
