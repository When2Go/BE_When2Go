package org.example.when2go.domain.trip.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.example.when2go.domain.trip.dto.AudioParseRequest;
import org.example.when2go.domain.trip.dto.NavigationParseResponse;
import org.example.when2go.domain.trip.error.NavigationParseErrorCode;
import org.example.when2go.global.error.DomainException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

// 실제 HTTP(Reactor Netty) 경로 검증. Gemini 대신 JDK 내장 HttpServer를 Mock 서버로 사용한다.
class GeminiAudioParseClientHttpTest {

    private static final String ONE_ITEM_BODY = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
            + "\"[{\\\"id\\\":0,\\\"start_location\\\":null,\\\"end_location\\\":\\\"강남역\\\","
            + "\\\"appointment_time\\\":null}]\"}]}}]}";

    private HttpServer server;
    private ExecutorService serverPool;
    private final CountDownLatch releaseServer = new CountDownLatch(1);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverPool = Executors.newCachedThreadPool();
        server.setExecutor(serverPool);
        server.start();
    }

    @AfterEach
    void tearDown() {
        releaseServer.countDown();
        server.stop(0);
        serverPool.shutdownNow();
    }

    private GeminiAudioParseClient client(Duration httpTimeout, WebClient.Builder builder) {
        WebClient webClient = builder
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta")
                .build();
        return new GeminiAudioParseClient(
                webClient,
                Clock.fixed(Instant.parse("2026-06-19T01:00:00Z"), ZoneOffset.UTC),
                "test-model",
                httpTimeout);
    }

    private GeminiAudioParseClient client(Duration httpTimeout) {
        return client(httpTimeout, WebClient.builder());
    }

    private List<AudioParseRequest> oneRequest(long id) {
        return List.of(new AudioParseRequest(id, new byte[]{1, 2, 3}, "audio/mp3"));
    }

    private void handle(HttpHandler handler) {
        server.createContext("/v1beta/models/test-model:generateContent", handler);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
        exchange.close();
    }

    @Test
    void 정상_응답을_Mono로_반환하고_요청은_JSON_바이트로_전송된다() {
        // given
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        handle(exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, ONE_ITEM_BODY);
        });

        // when
        Map<Long, NavigationParseResponse> result =
                client(Duration.ofSeconds(5)).parseBatch(oneRequest(100L)).block(Duration.ofSeconds(5));

        // then
        assertThat(result.get(100L).endLocation()).isEqualTo("강남역");
        assertThat(contentType.get()).startsWith("application/json");
        assertThat(requestBody.get()).contains("\"inline_data\"").contains("\"data\":\"AQID\"");
    }

    @Test
    void 구독_전에는_HTTP_요청을_보내지_않는다() throws Exception {
        // given
        CountDownLatch called = new CountDownLatch(1);
        handle(exchange -> {
            called.countDown();
            respond(exchange, 200, ONE_ITEM_BODY);
        });

        // when: Mono 생성만
        client(Duration.ofSeconds(5)).parseBatch(oneRequest(100L));

        // then
        assertThat(called.await(300, TimeUnit.MILLISECONDS)).isFalse();
    }

    @Test
    void HTTP_Timeout을_넘기면_TimeoutException으로_끝나고_진행중인_HTTP_교환이_취소된다() throws Exception {
        // given: 응답을 주지 않는 서버
        CountDownLatch requestArrived = new CountDownLatch(1);
        handle(exchange -> {
            requestArrived.countDown();
            try {
                releaseServer.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        CountDownLatch exchangeCancelled = new CountDownLatch(1);
        WebClient.Builder builder = WebClient.builder()
                .filter((request, next) -> next.exchange(request).doOnCancel(exchangeCancelled::countDown));

        // when
        CompletableFuture<Map<Long, NavigationParseResponse>> future =
                client(Duration.ofMillis(300), builder).parseBatch(oneRequest(100L)).toFuture();

        // then
        assertThat(requestArrived.await(5, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(TimeoutException.class);
        assertThat(exchangeCancelled.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void 서버_5xx면_PARSE_FAILED_DomainException으로_끝난다() {
        // given
        handle(exchange -> respond(exchange, 503, "{\"error\":\"overloaded\"}"));

        // when & then
        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).parseBatch(oneRequest(100L)).block())
                .isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getErrorCode())
                        .isEqualTo(NavigationParseErrorCode.PARSE_FAILED));
    }

    @Test
    void 응답_본문이_비어있으면_값_없이_끝나지_않고_DomainException으로_끝난다() {
        // given
        handle(exchange -> respond(exchange, 200, ""));

        // when & then
        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).parseBatch(oneRequest(100L)).block())
                .isInstanceOf(DomainException.class);
    }

    @Test
    void 두_배치의_HTTP_호출이_동시에_진행될_수_있다() throws Exception {
        // given: 서버는 두 요청이 모두 도착해야 응답한다. 순차 호출이면 첫 요청이 503으로 끝난다.
        CountDownLatch bothArrived = new CountDownLatch(2);
        handle(exchange -> {
            bothArrived.countDown();
            try {
                boolean concurrent = bothArrived.await(5, TimeUnit.SECONDS);
                respond(exchange, concurrent ? 200 : 503, ONE_ITEM_BODY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        GeminiAudioParseClient client = client(Duration.ofSeconds(10));

        // when
        CompletableFuture<Map<Long, NavigationParseResponse>> first = client.parseBatch(oneRequest(1L)).toFuture();
        CompletableFuture<Map<Long, NavigationParseResponse>> second = client.parseBatch(oneRequest(2L)).toFuture();

        // then
        assertThat(first.get(10, TimeUnit.SECONDS)).containsKey(1L);
        assertThat(second.get(10, TimeUnit.SECONDS)).containsKey(2L);
    }
}
