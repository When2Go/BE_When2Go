package org.example.when2go.domain.route.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.when2go.domain.route.dto.GoogleRouteSearchResponse;
import org.example.when2go.domain.route.dto.RouteSearchRequest;

@Tag(name = "경로", description = "경로 검색 API")
public interface RouteControllerApi {

    @Operation(
            summary = "대중교통 경로 검색",
            description = "출발지/도착지 좌표와 도착 희망 시각을 받아 길찾기 API를 호출하여 "
                    + "대중교통(TRANSIT) 경로 정보를 반환한다. "
                    + "응답은 Google Routes API의 응답 구조를 그대로 전달한다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "경로 검색 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "유효하지 않은 요청 (필수 필드 누락 또는 형식 오류)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = org.example.when2go.global.response.ApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "필수 필드 누락",
                                            value = "{\"success\": false, \"code\": \"GLOBAL_001\", \"message\": \"잘못된 입력값입니다.\"}"
                                    ),
                                    @ExampleObject(
                                            name = "arrivalTime 형식 오류",
                                            value = "{\"success\": false, \"code\": \"GLOBAL_002\", \"message\": \"잘못된 타입입니다.\"}"
                                    )
                            }
                    )
            )
    })
    org.example.when2go.global.response.ApiResponse<GoogleRouteSearchResponse> search(
            @Valid RouteSearchRequest routeSearchRequest
    );
}
