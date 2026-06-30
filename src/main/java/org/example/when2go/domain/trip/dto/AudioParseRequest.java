package org.example.when2go.domain.trip.dto;

// 배치 묶음의 한 건. id는 응답을 다시 요청 스레드로 분배할 때 매핑 키.
public record AudioParseRequest(
        Long id,
        byte[] audioBytes,
        String mimeType
) {
}
