package org.example.when2go.domain.trip.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.trip.client.GeminiAudioParseClient;
import org.example.when2go.domain.trip.dto.AudioParseRequest;
import org.example.when2go.domain.trip.dto.NavigationParseResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NavigationParseBatchProcessor {

    static final int CAPACITY = 10;
    static final int BATCH_MAX = 10;
    static final long TIMEOUT_SECONDS = 30;

    private final GeminiAudioParseClient client;

    // 처리 대기 큐. 동시 요청은 여기 쌓이고, 가득 차면 offer가 false 반환(논블로킹 거절).
    private final BlockingQueue<AudioParseRequest> queue = new LinkedBlockingQueue<>(CAPACITY);

    // {요청 id - 그 요청을 기다리는 CF} 매핑. 스케줄러가 응답을 받아 id별로 complete를 주입한다.
    private final ConcurrentHashMap<Long, CompletableFuture<NavigationParseResponse>> pending =
            new ConcurrentHashMap<>();

    public NavigationParseBatchProcessor(GeminiAudioParseClient client) {
        this.client = client;
    }

    public CompletableFuture<NavigationParseResponse> submit(AudioParseRequest req) {
        CompletableFuture<NavigationParseResponse> future = new CompletableFuture<>();
        pending.put(req.id(), future);

        // 큐 가득 → 즉시 거절. 백프레셔.
        if (!queue.offer(req)) {
            pending.remove(req.id());
            future.completeExceptionally(new IllegalStateException("처리 대기열이 가득 찼습니다"));
            return future;
        }

        // 같은 인스턴스에 적용 — orTimeout/whenComplete를 체이닝해 새 CF를 만들지 않고,
        // pending에 들어간 그 CF가 직접 타임아웃되고 직접 자기를 제거하도록 한다.
        future.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        future.whenComplete((res, ex) -> pending.remove(req.id()));
        return future;
    }

    // 이전 배치 호출이 끝난 후 1초 뒤에 다음 배치 시작. Gemini 호출이 길어도 큐는 그대로 쌓인다.
    // 직전 호출과 직렬화되므로 부하 자연 백오프 효과.
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.SECONDS)
    public void processBatch() {
        List<AudioParseRequest> batch = new ArrayList<>(BATCH_MAX);
        queue.drainTo(batch, BATCH_MAX);
        if (batch.isEmpty()) return;

        try {
            Map<Long, NavigationParseResponse> results = client.parseBatch(batch);

            for (AudioParseRequest req : batch) {
                CompletableFuture<NavigationParseResponse> f = pending.get(req.id());
                if (f == null) {
                    // 호출자 쪽이 타임아웃나서 이미 제거됨. 응답은 버린다.
                    log.warn("[NavigationParseBatch] pending 누락(이미 타임아웃 가능성). id={}", req.id());
                    continue;
                }
                NavigationParseResponse res = results.get(req.id());
                if (res != null) {
                    f.complete(res);
                } else {
                    f.completeExceptionally(
                            new IllegalStateException("Gemini 응답에 id 누락: id=" + req.id()));
                }
            }
        } catch (Exception e) {
            // 호출 자체 실패 → 배치 전체를 한꺼번에 실패 처리.
            // 안 하면 모든 요청 스레드가 타임아웃까지 멍하니 대기한다.
            log.error("[NavigationParseBatch] 배치 호출 실패. batchSize={}", batch.size(), e);
            for (AudioParseRequest req : batch) {
                CompletableFuture<NavigationParseResponse> f = pending.get(req.id());
                if (f != null) f.completeExceptionally(e);
            }
        }
    }
}
