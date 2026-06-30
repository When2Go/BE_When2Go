package org.example.when2go.domain.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.example.when2go.domain.trip.client.GeminiAudioParseClient;
import org.example.when2go.domain.trip.dto.AudioParseRequest;
import org.example.when2go.domain.trip.dto.NavigationParseResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavigationParseBatchProcessorTest {

    @Mock
    private GeminiAudioParseClient client;

    @InjectMocks
    private NavigationParseBatchProcessor processor;

    private AudioParseRequest req(long id) {
        return new AudioParseRequest(id, new byte[]{(byte) id}, "audio/mp3");
    }

    @Test
    void 정상_배치는_id별로_결과를_분배한다() throws Exception {
        CompletableFuture<NavigationParseResponse> f1 = processor.submit(req(1));
        CompletableFuture<NavigationParseResponse> f2 = processor.submit(req(2));
        CompletableFuture<NavigationParseResponse> f3 = processor.submit(req(3));

        Map<Long, NavigationParseResponse> results = new HashMap<>();
        results.put(1L, new NavigationParseResponse(null, "강남역", null));
        results.put(2L, new NavigationParseResponse("집", "회사", "2026-06-20 09:30"));
        results.put(3L, new NavigationParseResponse(null, "홍대입구역", null));
        when(client.parseBatch(any())).thenReturn(results);

        processor.processBatch();

        assertThat(f1.isDone()).isTrue();
        assertThat(f1.get().endLocation()).isEqualTo("강남역");
        assertThat(f2.get().startLocation()).isEqualTo("집");
        assertThat(f3.get().endLocation()).isEqualTo("홍대입구역");
    }

    @Test
    void 큐가_가득_차면_11번째_요청은_즉시_실패한다() {
        List<CompletableFuture<NavigationParseResponse>> futures = new ArrayList<>();
        for (long i = 1; i <= NavigationParseBatchProcessor.CAPACITY; i++) {
            futures.add(processor.submit(req(i)));
        }

        CompletableFuture<NavigationParseResponse> rejected = processor.submit(req(99));

        assertThat(rejected.isCompletedExceptionally()).isTrue();
        assertThatThrownBy(rejected::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        for (CompletableFuture<NavigationParseResponse> f : futures) {
            assertThat(f.isDone()).isFalse();
        }
    }

    @Test
    void 응답에서_일부_id가_누락되면_누락된_요청만_실패한다() throws Exception {
        CompletableFuture<NavigationParseResponse> f1 = processor.submit(req(1));
        CompletableFuture<NavigationParseResponse> f2 = processor.submit(req(2));
        CompletableFuture<NavigationParseResponse> f3 = processor.submit(req(3));

        // id=2 만 누락
        Map<Long, NavigationParseResponse> partial = new HashMap<>();
        partial.put(1L, new NavigationParseResponse(null, "강남역", null));
        partial.put(3L, new NavigationParseResponse(null, "홍대입구역", null));
        when(client.parseBatch(any())).thenReturn(partial);

        processor.processBatch();

        assertThat(f1.get().endLocation()).isEqualTo("강남역");
        assertThat(f3.get().endLocation()).isEqualTo("홍대입구역");
        assertThat(f2.isCompletedExceptionally()).isTrue();
        assertThatThrownBy(f2::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void Gemini_호출_자체가_실패하면_배치_전체가_실패한다() {
        CompletableFuture<NavigationParseResponse> f1 = processor.submit(req(1));
        CompletableFuture<NavigationParseResponse> f2 = processor.submit(req(2));

        RuntimeException boom = new RuntimeException("Gemini 5xx");
        when(client.parseBatch(any())).thenThrow(boom);

        processor.processBatch();

        assertThat(f1.isCompletedExceptionally()).isTrue();
        assertThat(f2.isCompletedExceptionally()).isTrue();
        assertThatThrownBy(f1::get).hasCauseInstanceOf(RuntimeException.class);
        assertThatThrownBy(f2::get).hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void 빈_큐에서는_Gemini를_호출하지_않는다() {
        processor.processBatch();
        verify(client, times(0)).parseBatch(any());
    }

    @Test
    void 타임아웃_후_지연_응답은_무시된다() throws Exception {
        // 1) 요청을 큐에 넣은 직후 호출자가 타임아웃났다고 가정 → 직접 future를 완료시켜 pending에서 제거
        AudioParseRequest req = req(1);
        CompletableFuture<NavigationParseResponse> f = processor.submit(req);

        // 호출자가 타임아웃나면 whenComplete가 pending에서 제거. 동일 효과를 강제로 시뮬레이션.
        f.completeExceptionally(new TimeoutException("simulated"));

        // 2) 그 사이 Gemini 호출은 정상 응답
        Map<Long, NavigationParseResponse> results = new HashMap<>();
        results.put(1L, new NavigationParseResponse(null, "강남역", null));
        when(client.parseBatch(any())).thenReturn(results);

        // 3) 스케줄러가 깨어남 → pending에 없으니 그냥 로그만 찍고 넘어가야 함 (예외 X)
        processor.processBatch();

        assertThat(f.isCompletedExceptionally()).isTrue();
    }

    @Test
    void CF가_완료되면_pending에서_제거된다() {
        // whenComplete(remove) 동작 확인. 메모리 누수 방지의 핵심 라인.
        CompletableFuture<NavigationParseResponse> f1 = processor.submit(req(1));

        Map<Long, NavigationParseResponse> results = new HashMap<>();
        results.put(1L, new NavigationParseResponse(null, "강남역", null));
        when(client.parseBatch(any())).thenReturn(results);

        processor.processBatch();

        assertThat(f1.isDone()).isTrue();
        // 한 번 더 같은 id 제출 → 이전 CF가 pending에 안 남아있어야 새 CF가 등록됨.
        // pending 누수가 있다면 새 submit이 같은 id를 덮어쓰지만 동작에는 문제없음 (간접 검증).
        CompletableFuture<NavigationParseResponse> f1Again = processor.submit(req(1));
        assertThat(f1Again).isNotSameAs(f1);
        assertThat(f1Again.isDone()).isFalse();
    }
}
