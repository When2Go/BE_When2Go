package org.example.when2go.domain.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.example.when2go.domain.trip.client.GeminiAudioParseClient;
import org.example.when2go.domain.trip.dto.AudioParseRequest;
import org.example.when2go.domain.trip.dto.NavigationParseResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@ExtendWith(MockitoExtension.class)
class NavigationParseBatchProcessorTest {

    @Mock
    private GeminiAudioParseClient client;

    private AudioParseRequest req(long id) {
        return new AudioParseRequest(id, new byte[]{(byte) id}, "audio/mp3");
    }

    private NavigationParseResponse res(String endLocation) {
        return new NavigationParseResponse(null, endLocation, null);
    }

    // 주기 안에 허가권이 충분한 RateLimiter (허가 제약 없이 배치 동작만 검증할 때)
    private RateLimiter permissiveLimiter() {
        return limiter(100);
    }

    // 주기(1시간) 안에 permits개만 허가하는 RateLimiter. timeout 0 → 허가 없으면 즉시 false.
    private RateLimiter limiter(int permits) {
        return RateLimiter.of("test", RateLimiterConfig.custom()
                .limitForPeriod(permits)
                .limitRefreshPeriod(Duration.ofHours(1))
                .timeoutDuration(Duration.ZERO)
                .build());
    }

    private NavigationParseBatchProcessor processor(RateLimiter rateLimiter) {
        return new NavigationParseBatchProcessor(client, rateLimiter);
    }

    // --- RateLimiter / Queue ---

    @Test
    void 허가를_얻으면_배치를_호출하고_id별로_결과를_분배한다() throws Exception {
        // given
        NavigationParseBatchProcessor processor = processor(permissiveLimiter());
        CompletableFuture<NavigationParseResponse> f1 = processor.submit(req(1));
        CompletableFuture<NavigationParseResponse> f2 = processor.submit(req(2));
        CompletableFuture<NavigationParseResponse> f3 = processor.submit(req(3));

        Map<Long, NavigationParseResponse> results = new HashMap<>();
        results.put(1L, res("강남역"));
        results.put(2L, new NavigationParseResponse("집", "회사", "2026-06-20 09:30"));
        results.put(3L, res("홍대입구역"));
        when(client.parseBatch(any())).thenReturn(Mono.just(results));

        // when
        processor.processBatch();

        // then
        assertThat(f1.get().endLocation()).isEqualTo("강남역");
        assertThat(f2.get().startLocation()).isEqualTo("집");
        assertThat(f3.get().endLocation()).isEqualTo("홍대입구역");
        assertThat(processor.pendingCount()).isZero();
        assertThat(processor.queuedCount()).isZero();
    }

    @Test
    void 허가를_얻지_못하면_큐에서_꺼내지_않고_호출하지_않는다() {
        // given: 이번 주기의 허가권을 이미 다 썼다
        RateLimiter rateLimiter = limiter(1);
        rateLimiter.acquirePermission();
        NavigationParseBatchProcessor processor = processor(rateLimiter);
        CompletableFuture<NavigationParseResponse> f1 = processor.submit(req(1));
        CompletableFuture<NavigationParseResponse> f2 = processor.submit(req(2));

        // when
        processor.processBatch();

        // then: 요청은 큐에 그대로 남아 다음 주기를 기다린다
        verify(client, never()).parseBatch(any());
        assertThat(processor.queuedCount()).isEqualTo(2);
        assertThat(f1.isDone()).isFalse();
        assertThat(f2.isDone()).isFalse();
    }

    @Test
    void 빈_큐에서는_허가권을_소비하지_않고_호출하지_않는다() {
        // given
        RateLimiter rateLimiter = limiter(1);
        NavigationParseBatchProcessor processor = processor(rateLimiter);

        // when
        processor.processBatch();

        // then
        verify(client, never()).parseBatch(any());
        assertThat(rateLimiter.getMetrics().getAvailablePermissions()).isEqualTo(1);
    }

    @Test
    void 큐가_가득_차면_11번째_요청은_즉시_실패한다() {
        // given
        NavigationParseBatchProcessor processor = processor(permissiveLimiter());
        List<CompletableFuture<NavigationParseResponse>> futures = new ArrayList<>();
        for (long i = 1; i <= NavigationParseBatchProcessor.CAPACITY; i++) {
            futures.add(processor.submit(req(i)));
        }

        // when
        CompletableFuture<NavigationParseResponse> rejected = processor.submit(req(99));

        // then
        assertThat(rejected.isCompletedExceptionally()).isTrue();
        assertThatThrownBy(rejected::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        for (CompletableFuture<NavigationParseResponse> f : futures) {
            assertThat(f.isDone()).isFalse();
        }
        // 거절된 요청은 pending에 남지 않는다
        assertThat(processor.pendingCount()).isEqualTo(NavigationParseBatchProcessor.CAPACITY);
    }

    @Test
    void 허가가_없어_큐가_차_있으면_새_요청은_거절된다() {
        // given: 허가권 소진 → 스케줄러가 큐를 비우지 못한다 (허가 없이 미리 꺼내 두지 않음)
        RateLimiter rateLimiter = limiter(1);
        rateLimiter.acquirePermission();
        NavigationParseBatchProcessor processor = processor(rateLimiter);
        for (long i = 1; i <= NavigationParseBatchProcessor.CAPACITY; i++) {
            processor.submit(req(i));
        }
        processor.processBatch();

        // when
        CompletableFuture<NavigationParseResponse> rejected = processor.submit(req(99));

        // then: Bounded Queue 기반 Backpressure가 그대로 유지된다
        assertThat(rejected.isCompletedExceptionally()).isTrue();
        assertThat(processor.queuedCount()).isEqualTo(NavigationParseBatchProcessor.CAPACITY);
    }

    // --- 논블로킹 / 동시 진행 ---

    @Test
    void Gemini_응답이_지연돼도_스케줄러는_응답을_기다리지_않고_반환한다() {
        // given: 응답이 영원히 오지 않는 호출
        NavigationParseBatchProcessor processor = processor(permissiveLimiter());
        CompletableFuture<NavigationParseResponse> f1 = processor.submit(req(1));
        when(client.parseBatch(any())).thenReturn(Mono.never());

        // when & then: block() 하던 기존 구조라면 여기서 멈춘다
        assertTimeoutPreemptively(Duration.ofSeconds(1), processor::processBatch);
        verify(client, times(1)).parseBatch(any());
        assertThat(f1.isDone()).isFalse();
    }

    @Test
    void 이전_배치_응답_전에_다음_배치가_호출되어_동시에_진행된다() {
        // given
        NavigationParseBatchProcessor processor = processor(permissiveLimiter());
        Sinks.One<Map<Long, NavigationParseResponse>> first = Sinks.one();
        Sinks.One<Map<Long, NavigationParseResponse>> second = Sinks.one();
        when(client.parseBatch(any())).thenReturn(first.asMono(), second.asMono());

        CompletableFuture<NavigationParseResponse> f1 = processor.submit(req(1));
        processor.processBatch();
        CompletableFuture<NavigationParseResponse> f2 = processor.submit(req(2));

        // when: 첫 배치 응답이 오기 전 다음 tick
        processor.processBatch();

        // then: 두 호출이 모두 나간 상태(in-flight 2)
        verify(client, times(2)).parseBatch(any());
        assertThat(f1.isDone()).isFalse();
        assertThat(f2.isDone()).isFalse();
        assertThat(processor.pendingCount()).isEqualTo(2);
    }

    @Test
    void 배치_응답_순서가_역전돼도_올바른_Future에_전달된다() throws Exception {
        // given: 배치1(id 1,2), 배치2(id 3)가 모두 진행 중
        NavigationParseBatchProcessor processor = processor(permissiveLimiter());
        Sinks.One<Map<Long, NavigationParseResponse>> first = Sinks.one();
        Sinks.One<Map<Long, NavigationParseResponse>> second = Sinks.one();
        when(client.parseBatch(any())).thenReturn(first.asMono(), second.asMono());

        CompletableFuture<NavigationParseResponse> f1 = processor.submit(req(1));
        CompletableFuture<NavigationParseResponse> f2 = processor.submit(req(2));
        processor.processBatch();
        CompletableFuture<NavigationParseResponse> f3 = processor.submit(req(3));
        processor.processBatch();

        // when: 나중에 보낸 배치2가 먼저 응답
        second.tryEmitValue(Map.of(3L, res("홍대입구역")));
        assertThat(f3.get().endLocation()).isEqualTo("홍대입구역");
        assertThat(f1.isDone()).isFalse();

        first.tryEmitValue(Map.of(1L, res("강남역"), 2L, res("역삼역")));

        // then
        assertThat(f1.get().endLocation()).isEqualTo("강남역");
        assertThat(f2.get().endLocation()).isEqualTo("역삼역");
        assertThat(processor.pendingCount()).isZero();
    }

    // --- 실패 / Timeout ---

    @Test
    void 응답에서_일부_id가_누락되면_누락된_요청만_실패한다() throws Exception {
        // given
        NavigationParseBatchProcessor processor = processor(permissiveLimiter());
        CompletableFuture<NavigationParseResponse> f1 = processor.submit(req(1));
        CompletableFuture<NavigationParseResponse> f2 = processor.submit(req(2));
        CompletableFuture<NavigationParseResponse> f3 = processor.submit(req(3));

        Map<Long, NavigationParseResponse> partial = new HashMap<>();
        partial.put(1L, res("강남역"));
        partial.put(3L, res("홍대입구역"));
        when(client.parseBatch(any())).thenReturn(Mono.just(partial));

        // when
        processor.processBatch();

        // then
        assertThat(f1.get().endLocation()).isEqualTo("강남역");
        assertThat(f3.get().endLocation()).isEqualTo("홍대입구역");
        assertThatThrownBy(f2::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void Gemini_호출이_실패하면_배치_전체가_예외_완료되고_pending이_정리된다() {
        // given
        NavigationParseBatchProcessor processor = processor(permissiveLimiter());
        Sinks.One<Map<Long, NavigationParseResponse>> sink = Sinks.one();
        when(client.parseBatch(any())).thenReturn(sink.asMono());
        CompletableFuture<NavigationParseResponse> f1 = processor.submit(req(1));
        CompletableFuture<NavigationParseResponse> f2 = processor.submit(req(2));
        processor.processBatch();

        // when
        RuntimeException boom = new RuntimeException("Gemini 5xx");
        sink.tryEmitError(boom);

        // then
        assertThatThrownBy(f1::get).hasCause(boom);
        assertThatThrownBy(f2::get).hasCause(boom);
        assertThat(processor.pendingCount()).isZero();
    }

    @Test
    void 호출_생성_중_동기_예외가_나도_배치가_예외_완료된다() {
        // given
        NavigationParseBatchProcessor processor = processor(permissiveLimiter());
        CompletableFuture<NavigationParseResponse> f1 = processor.submit(req(1));
        when(client.parseBatch(any())).thenThrow(new RuntimeException("직렬화 실패"));

        // when
        processor.processBatch();

        // then
        assertThat(f1.isCompletedExceptionally()).isTrue();
        assertThat(processor.pendingCount()).isZero();
    }

    @Test
    void HTTP_Timeout이면_배치_Future가_TimeoutException으로_완료되고_정리된다() {
        // given
        NavigationParseBatchProcessor processor = processor(permissiveLimiter());
        Sinks.One<Map<Long, NavigationParseResponse>> sink = Sinks.one();
        when(client.parseBatch(any())).thenReturn(sink.asMono());
        CompletableFuture<NavigationParseResponse> f1 = processor.submit(req(1));
        processor.processBatch();

        // when: 클라이언트의 Mono.timeout이 내보내는 시그널
        sink.tryEmitError(new TimeoutException("Did not observe any item within 25s"));

        // then: Service가 PARSE_TIMEOUT(504)으로 변환하는 TimeoutException 그대로 전달
        assertThatThrownBy(f1::get).hasCauseInstanceOf(TimeoutException.class);
        assertThat(processor.pendingCount()).isZero();
    }

    // --- 만료/취소 요청 ---

    @Test
    void 이미_만료된_요청은_큐에서_빠지고_Gemini_호출_대상에서_제외된다() {
        // given
        NavigationParseBatchProcessor processor = processor(permissiveLimiter());
        processor.submit(req(1));
        CompletableFuture<NavigationParseResponse> expired = processor.submit(req(2));
        processor.submit(req(3));
        when(client.parseBatch(any())).thenReturn(Mono.never());

        // when: 큐 대기 중 Future Timeout(orTimeout과 동일한 시그널)
        expired.completeExceptionally(new TimeoutException("simulated"));
        processor.processBatch();

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AudioParseRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(client).parseBatch(captor.capture());
        assertThat(captor.getValue()).extracting(AudioParseRequest::id).containsExactly(1L, 3L);
    }

    @Test
    void 만료된_요청만_남으면_허가권을_소비하지_않고_호출하지_않는다() {
        // given
        RateLimiter rateLimiter = limiter(1);
        NavigationParseBatchProcessor processor = processor(rateLimiter);
        CompletableFuture<NavigationParseResponse> expired = processor.submit(req(1));
        expired.completeExceptionally(new TimeoutException("simulated"));

        // when
        processor.processBatch();

        // then: 만료 시점에 큐에서 빠졌으므로 빈 큐로 판단
        verify(client, never()).parseBatch(any());
        assertThat(rateLimiter.getMetrics().getAvailablePermissions()).isEqualTo(1);
        assertThat(processor.queuedCount()).isZero();
    }

    @Test
    void 만료된_요청이_빠지면_큐_용량이_반환된다() {
        // given: 큐 가득
        NavigationParseBatchProcessor processor = processor(permissiveLimiter());
        List<CompletableFuture<NavigationParseResponse>> futures = new ArrayList<>();
        for (long i = 1; i <= NavigationParseBatchProcessor.CAPACITY; i++) {
            futures.add(processor.submit(req(i)));
        }

        // when
        futures.get(0).completeExceptionally(new TimeoutException("simulated"));
        CompletableFuture<NavigationParseResponse> next = processor.submit(req(99));

        // then
        assertThat(next.isDone()).isFalse();
        assertThat(processor.queuedCount()).isEqualTo(NavigationParseBatchProcessor.CAPACITY);
    }

    @Test
    void 취소된_요청은_pending과_큐에서_정리된다() {
        // given
        NavigationParseBatchProcessor processor = processor(permissiveLimiter());
        CompletableFuture<NavigationParseResponse> f1 = processor.submit(req(1));

        // when
        f1.cancel(true);

        // then
        assertThatThrownBy(f1::get).isInstanceOf(CancellationException.class);
        assertThat(processor.pendingCount()).isZero();
        assertThat(processor.queuedCount()).isZero();
    }

    // --- 완료 경합 ---

    @Test
    void 응답_대기_중_Timeout된_요청은_뒤늦은_응답으로_덮어쓰지_않는다() throws Exception {
        // given: 배치(id 1,2) 진행 중
        NavigationParseBatchProcessor processor = processor(permissiveLimiter());
        Sinks.One<Map<Long, NavigationParseResponse>> sink = Sinks.one();
        when(client.parseBatch(any())).thenReturn(sink.asMono());
        CompletableFuture<NavigationParseResponse> timedOut = processor.submit(req(1));
        CompletableFuture<NavigationParseResponse> alive = processor.submit(req(2));
        processor.processBatch();

        // when: id=1만 먼저 Timeout → 이후 응답 도착
        timedOut.completeExceptionally(new TimeoutException("simulated"));
        sink.tryEmitValue(Map.of(1L, res("강남역"), 2L, res("역삼역")));

        // then
        assertThatThrownBy(timedOut::get).hasCauseInstanceOf(TimeoutException.class);
        assertThat(alive.get().endLocation()).isEqualTo("역삼역");
        assertThat(processor.pendingCount()).isZero();
    }

    @Test
    void 정상_응답과_Timeout이_동시에_도착해도_Future는_한_번만_완료된다() throws Exception {
        NavigationParseBatchProcessor processor = processor(permissiveLimiter());
        AtomicReference<Sinks.One<Map<Long, NavigationParseResponse>>> currentSink = new AtomicReference<>();
        when(client.parseBatch(any())).thenAnswer(inv -> {
            Sinks.One<Map<Long, NavigationParseResponse>> sink = Sinks.one();
            currentSink.set(sink);
            return sink.asMono();
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (long id = 1; id <= 200; id++) {
                // given
                CompletableFuture<NavigationParseResponse> f = processor.submit(req(id));
                AtomicInteger callbackCount = new AtomicInteger();
                f.whenComplete((r, e) -> callbackCount.incrementAndGet());
                processor.processBatch();
                Sinks.One<Map<Long, NavigationParseResponse>> sink = currentSink.get();

                // when: Event Loop(응답)와 Timer(orTimeout) 스레드가 같은 Future를 동시에 완료 시도
                CountDownLatch start = new CountDownLatch(1);
                long finalId = id;
                var response = pool.submit(() -> {
                    start.await();
                    return sink.tryEmitValue(Map.of(finalId, res("강남역")));
                });
                var timeout = pool.submit(() -> {
                    start.await();
                    return f.completeExceptionally(new TimeoutException("race"));
                });
                start.countDown();
                response.get(1, TimeUnit.SECONDS);
                timeout.get(1, TimeUnit.SECONDS);

                // then: 둘 중 하나만 반영되고, 콜백(DeferredResult 세팅)은 정확히 1회
                assertThat(f.isDone()).isTrue();
                assertThat(callbackCount.get()).isEqualTo(1);
                if (f.isCompletedExceptionally()) {
                    assertThatThrownBy(f::get).hasCauseInstanceOf(TimeoutException.class);
                } else {
                    assertThat(f.get().endLocation()).isEqualTo("강남역");
                }
            }
            assertThat(processor.pendingCount()).isZero();
        } finally {
            pool.shutdownNow();
        }
    }
}
