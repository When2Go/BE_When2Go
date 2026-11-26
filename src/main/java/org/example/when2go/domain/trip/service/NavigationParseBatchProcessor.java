package org.example.when2go.domain.trip.service;

import io.github.resilience4j.ratelimiter.RateLimiter;
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

    // Gemini 호출량 제어. 스케줄러는 "언제 확인할지"만, 호출 허가는 RateLimiter가 결정한다.
    private final RateLimiter rateLimiter;

    // 처리 대기 큐. 동시 요청은 여기 쌓이고, 가득 차면 offer가 false 반환(논블로킹 거절).
    private final BlockingQueue<AudioParseRequest> queue = new LinkedBlockingQueue<>(CAPACITY);

    // {요청 id - 그 요청을 기다리는 CF} 매핑. 응답 도착 시 id별로 complete를 주입한다.
    private final ConcurrentHashMap<Long, CompletableFuture<NavigationParseResponse>> pending =
            new ConcurrentHashMap<>();

    public NavigationParseBatchProcessor(
            GeminiAudioParseClient client,
            RateLimiter geminiAudioParseRateLimiter
    ) {
        this.client = client;
        this.rateLimiter = geminiAudioParseRateLimiter;
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
        future.whenComplete((res, ex) -> {
            pending.remove(req.id());
            // Timeout/취소로 끝났는데 아직 큐에 남아 있다면 빼서, 큐 용량을 돌려주고 Gemini 호출 대상에서 제외한다.
            if (ex != null) {
                queue.remove(req);
            }
        });
        return future;
    }

    // 스케줄러 스레드는 [큐 확인 → 허가 획득 → drain → 구독]까지만 하고 즉시 반환한다.
    // HTTP 응답은 기다리지 않으므로, 이전 배치가 진행 중이어도 다음 배치가 허가를 받아 동시에 나갈 수 있다.
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.SECONDS)
    public void processBatch() {
        // 1) 보낼 요청이 없으면 허가권을 소비하지 않는다.
        if (queue.isEmpty()) return;

        // 2) 허가 먼저. 실패 시 큐는 건드리지 않고 반환 → 요청은 큐에 남고, 큐 용량 기반 거절 정책도 그대로 유지된다.
        if (!rateLimiter.acquirePermission()) return;

        // 3) 허가를 얻은 뒤에만 drain. 그 사이 끝난(Timeout/취소) 요청은 호출 대상에서 뺀다.
        List<AudioParseRequest> drained = new ArrayList<>(BATCH_MAX);
        queue.drainTo(drained, BATCH_MAX);
        List<AudioParseRequest> batch = excludeCompleted(drained);
        if (batch.isEmpty()) return;

        // 4) 논블로킹 호출. 결과 콜백은 응답이 도착한 Event Loop 스레드에서 실행된다.
        try {
            client.parseBatch(batch).subscribe(
                    results -> completeBatch(batch, results),
                    e -> failBatch(batch, e)
            );
        } catch (RuntimeException e) {
            failBatch(batch, e);
        }
    }

    private List<AudioParseRequest> excludeCompleted(List<AudioParseRequest> drained) {
        List<AudioParseRequest> active = new ArrayList<>(drained.size());
        for (AudioParseRequest req : drained) {
            CompletableFuture<NavigationParseResponse> f = pending.get(req.id());
            if (f != null && !f.isDone()) {
                active.add(req);
            }
        }
        return active;
    }

    private void completeBatch(List<AudioParseRequest> batch, Map<Long, NavigationParseResponse> results) {
        for (AudioParseRequest req : batch) {
            CompletableFuture<NavigationParseResponse> f = pending.get(req.id());
            if (f == null) {
                // 응답 대기 중 호출자 쪽 Timeout으로 이미 제거됨. 응답은 버린다.
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
    }

    // 호출 자체 실패(HTTP 오류/Timeout/파싱 실패) → 배치 전체를 한꺼번에 실패 처리.
    // 안 하면 모든 요청이 Future Timeout까지 멍하니 대기한다.
    private void failBatch(List<AudioParseRequest> batch, Throwable e) {
        log.error("[NavigationParseBatch] 배치 호출 실패. batchSize={}", batch.size(), e);
        for (AudioParseRequest req : batch) {
            CompletableFuture<NavigationParseResponse> f = pending.get(req.id());
            if (f != null) f.completeExceptionally(e);
        }
    }

    // 테스트 검증용
    int pendingCount() {
        return pending.size();
    }

    int queuedCount() {
        return queue.size();
    }
}
