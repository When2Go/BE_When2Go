package org.example.when2go.domain.trip.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.trip.dto.AudioParseRequest;
import org.example.when2go.domain.trip.dto.NavigationParseResponse;
import org.example.when2go.domain.trip.error.NavigationParseErrorCode;
import org.example.when2go.global.error.DomainException;
import org.example.when2go.global.response.ApiResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class NavigationParseService {

    private final NavigationParseBatchProcessor processor;

    // 배치 orTimeout(30s) + 여유 2s. 배치 타임아웃이 먼저 터지도록 여유를 둔다.
    static final long DEFERRED_TIMEOUT_MS = 32_000L;

    // JVM 안에서만 충돌 안 나면 충분. 재시작 시 0부터 다시 시작해도 무방.
    private static final AtomicLong ID_GEN = new AtomicLong(0);

    private static final Map<String, String> MIME_MAP = Map.of(
            "mp3", "audio/mp3",
            "wav", "audio/wav",
            "m4a", "audio/mp4",
            "aac", "audio/aac",
            "flac", "audio/flac",
            "ogg", "audio/ogg"
    );

    public DeferredResult<ApiResponse<NavigationParseResponse>> parseAsync(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new DomainException(NavigationParseErrorCode.INVALID_AUDIO);
        }

        byte[] audioBytes;
        try {
            audioBytes = audio.getBytes();
        } catch (IOException e) {
            throw new DomainException(NavigationParseErrorCode.INVALID_AUDIO, e);
        }

        String mimeType = resolveMimeType(audio.getOriginalFilename());
        long id = ID_GEN.incrementAndGet();
        AudioParseRequest req = new AudioParseRequest(id, audioBytes, mimeType);

        DeferredResult<ApiResponse<NavigationParseResponse>> deferred =
                new DeferredResult<>(DEFERRED_TIMEOUT_MS);
        deferred.onTimeout(() ->
                deferred.setErrorResult(new DomainException(NavigationParseErrorCode.PARSE_TIMEOUT)));

        processor.submit(req).whenComplete((res, ex) -> {
            if (ex == null) {
                deferred.setResult(ApiResponse.success(res));
            } else {
                deferred.setErrorResult(toDomainException(ex));
            }
        });

        return deferred;
    }

    private DomainException toDomainException(Throwable ex) {
        Throwable cause = (ex instanceof CompletionException && ex.getCause() != null)
                ? ex.getCause() : ex;
        if (cause instanceof TimeoutException) {
            return new DomainException(NavigationParseErrorCode.PARSE_TIMEOUT, cause);
        }
        if (cause instanceof IllegalStateException
                && cause.getMessage() != null
                && cause.getMessage().contains("처리 대기열이 가득 찼습니다")) {
            return new DomainException(NavigationParseErrorCode.PARSE_QUEUE_FULL, cause);
        }
        return new DomainException(NavigationParseErrorCode.PARSE_FAILED, cause);
    }

    private String resolveMimeType(String filename) {
        if (filename == null) {
            throw new DomainException(NavigationParseErrorCode.INVALID_AUDIO);
        }

        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new DomainException(NavigationParseErrorCode.INVALID_AUDIO);
        }

        String ext = filename.substring(dotIndex + 1).toLowerCase();
        return MIME_MAP.getOrDefault(ext, "audio/" + ext);
    }
}
