package org.example.when2go.domain.trip.service;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.example.when2go.domain.trip.client.GeminiAudioParseClient;
import org.example.when2go.domain.trip.dto.AudioParseRequest;
import org.example.when2go.domain.trip.dto.NavigationParseResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 배치 처리 구조 처리량 측정 하네스. 일반 테스트에서는 실행되지 않는다.
 *
 * <p>실행: {@code WHEN2GO_PERF=true ./gradlew test --tests "*NavigationParseBatchLoadTest" --rerun}
 * <br>옵션(env): PERF_DELAYS_MS(기본 5000,10000,15000), PERF_DURATION_S(기본 120), PERF_RATE_PER_SEC(기본 2),
 * PERF_LABEL(결과 파일명 접두사), PERF_REFRESH_MS(RateLimiter 주기, 기본 4000)
 *
 * <p>Mock Gemini(JDK HttpServer)가 지정 지연 후 응답하고, 실제 WebClient(Reactor Netty)로 호출한다.
 * 스케줄러는 Spring Boot 기본값과 같은 단일 스레드 fixedDelay(1s)로 processBatch를 실행한다.
 * 요청 id를 오디오 바이트에 넣어, Mock 서버 도착 시각으로 요청별 큐 대기시간을 계산한다.
 */
@EnabledIfEnvironmentVariable(named = "WHEN2GO_PERF", matches = "true")
class NavigationParseBatchLoadTest {

    private static final Pattern DATA = Pattern.compile("\"data\":\"([^\"]+)\"");

    // --- 측정 대상 생성 (구조별로 이 두 메서드만 다르다) ---

    private GeminiAudioParseClient newClient(WebClient webClient) {
        return new GeminiAudioParseClient(webClient, Clock.systemUTC(), "perf-model", Duration.ofSeconds(25));
    }

    private NavigationParseBatchProcessor newProcessor(GeminiAudioParseClient client) {
        // application.yml 기본값과 동일 (PERF_REFRESH_MS로 주기만 바꿔 비교 가능)
        RateLimiter rateLimiter = RateLimiter.of("perf", RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofMillis(Long.parseLong(env("PERF_REFRESH_MS", "4000"))))
                .timeoutDuration(Duration.ZERO)
                .build());
        return new NavigationParseBatchProcessor(client, rateLimiter);
    }

    @Test
    void 지연별_처리량_측정() throws Exception {
        String label = env("PERF_LABEL", "run");
        int durationSec = Integer.parseInt(env("PERF_DURATION_S", "120"));
        double ratePerSec = Double.parseDouble(env("PERF_RATE_PER_SEC", "2"));
        StringBuilder report = new StringBuilder();
        for (String d : env("PERF_DELAYS_MS", "5000,10000,15000").split(",")) {
            report.append(runOnce(label, Long.parseLong(d.trim()), durationSec, ratePerSec)).append('\n');
        }
        Path out = Path.of("build", "perf", label + ".txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString());
        System.out.println(report);
    }

    private String runOnce(String label, long delayMs, int durationSec, double ratePerSec) throws Exception {
        long t0 = System.nanoTime();
        List<Long> callTimes = new CopyOnWriteArrayList<>();
        Map<Long, Long> arrivedAt = new ConcurrentHashMap<>();
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();

        // Mock Gemini
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService serverPool = Executors.newCachedThreadPool();
        server.setExecutor(serverPool);
        server.createContext("/v1beta/models/perf-model:generateContent", exchange -> {
            long now = System.nanoTime();
            callTimes.add(now);
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Matcher m = DATA.matcher(body);
                int n = 0;
                while (m.find()) {
                    long id = ByteBuffer.wrap(Base64.getDecoder().decode(m.group(1))).getLong();
                    arrivedAt.put(id, now);
                    n++;
                }
                Thread.sleep(delayMs);
                respond(exchange.getResponseBody(), exchange, n);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                inFlight.decrementAndGet();
                exchange.close();
            }
        });
        server.start();

        WebClient webClient = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(20 * 1024 * 1024))
                .build();
        NavigationParseBatchProcessor processor = newProcessor(newClient(webClient));

        // Spring Boot 기본 스케줄러와 동일: 단일 스레드 + fixedDelay 1s
        List<Long> schedulerNanos = new CopyOnWriteArrayList<>();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(() -> {
            long s = System.nanoTime();
            try {
                processor.processBatch();
            } finally {
                schedulerNanos.add(System.nanoTime() - s);
            }
        }, 1, 1, TimeUnit.SECONDS);

        // 요청 유입: 고정 속도
        Map<Long, Long> submittedAt = new ConcurrentHashMap<>();
        Map<Long, Long> completedAt = new ConcurrentHashMap<>();
        List<CompletableFuture<NavigationParseResponse>> futures = Collections.synchronizedList(new ArrayList<>());
        AtomicLong ids = new AtomicLong();
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger timedOut = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        ScheduledExecutorService arrivals = Executors.newSingleThreadScheduledExecutor();
        long periodMicros = (long) (1_000_000 / ratePerSec);
        arrivals.scheduleAtFixedRate(() -> {
            long id = ids.incrementAndGet();
            byte[] audio = ByteBuffer.allocate(Long.BYTES).putLong(id).array();
            submittedAt.put(id, System.nanoTime());
            CompletableFuture<NavigationParseResponse> f =
                    processor.submit(new AudioParseRequest(id, audio, "audio/mp3"));
            futures.add(f);
            f.whenComplete((r, e) -> {
                completedAt.put(id, System.nanoTime());
                Throwable cause = e instanceof java.util.concurrent.CompletionException && e.getCause() != null
                        ? e.getCause() : e;
                if (cause == null) success.incrementAndGet();
                else if (cause instanceof IllegalStateException
                        && cause.getMessage().contains("대기열")) rejected.incrementAndGet();
                else if (cause instanceof TimeoutException) timedOut.incrementAndGet();
                else failed.incrementAndGet();
            });
        }, 0, periodMicros, TimeUnit.MICROSECONDS);

        Thread.sleep(durationSec * 1000L);
        arrivals.shutdownNow();
        arrivals.awaitTermination(5, TimeUnit.SECONDS);
        long windowEnd = System.nanoTime();
        // 잔여 요청 정리 대기 (Future Timeout 30s + 여유)
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .handle((r, e) -> null).get(40, TimeUnit.SECONDS);
        scheduler.shutdownNow();
        server.stop(0);
        serverPool.shutdownNow();

        // --- 집계 ---
        double minutes = durationSec / 60.0;
        List<Long> callsInWindow = callTimes.stream().filter(t -> t <= windowEnd).sorted().toList();
        int maxPerSliding60 = 0;
        for (int i = 0, j = 0; i < callsInWindow.size(); i++) {
            while (callsInWindow.get(i) - callsInWindow.get(j) >= TimeUnit.SECONDS.toNanos(60)) j++;
            maxPerSliding60 = Math.max(maxPerSliding60, i - j + 1);
        }
        long minGapMs = Long.MAX_VALUE;
        for (int i = 1; i < callsInWindow.size(); i++) {
            minGapMs = Math.min(minGapMs, (callsInWindow.get(i) - callsInWindow.get(i - 1)) / 1_000_000);
        }
        // futures는 단일 유입 스레드가 id 순서대로 추가하므로 index = id - 1
        long successInWindow = 0;
        List<Long> queueWaitMs = new ArrayList<>();
        List<Long> e2eMs = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            long id = i + 1;
            CompletableFuture<NavigationParseResponse> f = futures.get(i);
            if (!f.isDone() || f.isCompletedExceptionally()) continue;
            if (completedAt.get(id) <= windowEnd) successInWindow++;
            queueWaitMs.add((arrivedAt.get(id) - submittedAt.get(id)) / 1_000_000);
            e2eMs.add((completedAt.get(id) - submittedAt.get(id)) / 1_000_000);
        }
        List<Long> schedMs = schedulerNanos.stream().map(n -> n / 1_000_000).toList();

        return String.format(
                "[%s] delay=%dms duration=%ds rate=%.1f/s%n"
                        + "  유입=%d 성공=%d 거절=%d 타임아웃=%d 기타실패=%d%n"
                        + "  Gemini 호출: 총 %d회(측정구간), 분당 %.1f회, 슬라이딩60s 최대 %d회, 최소 호출간격 %dms%n"
                        + "  사용자 처리량: 분당 %.1f건(측정구간 내 성공 완료)%n"
                        + "  동시 진행 Gemini HTTP 최대 %d%n"
                        + "  Scheduler 실행시간 ms: avg=%.1f p95=%d max=%d (실행 %d회)%n"
                        + "  큐 대기 ms(성공): p50=%d p95=%d max=%d%n"
                        + "  전체 응답시간 ms(성공): p50=%d p95=%d max=%d%n",
                label, delayMs, durationSec, ratePerSec,
                futures.size(), success.get(), rejected.get(), timedOut.get(), failed.get(),
                callsInWindow.size(), callsInWindow.size() / minutes, maxPerSliding60,
                minGapMs == Long.MAX_VALUE ? -1 : minGapMs,
                successInWindow / minutes,
                maxInFlight.get(),
                schedMs.stream().mapToLong(Long::longValue).average().orElse(0),
                pct(schedMs, 95), pct(schedMs, 100), schedMs.size(),
                pct(queueWaitMs, 50), pct(queueWaitMs, 95), pct(queueWaitMs, 100),
                pct(e2eMs, 50), pct(e2eMs, 95), pct(e2eMs, 100));
    }

    private static void respond(OutputStream os, com.sun.net.httpserver.HttpExchange exchange, int n)
            throws IOException {
        StringBuilder items = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            if (i > 0) items.append(',');
            items.append("{\\\"id\\\":").append(i)
                    .append(",\\\"start_location\\\":null,\\\"end_location\\\":\\\"강남역\\\",\\\"appointment_time\\\":null}");
        }
        items.append(']');
        byte[] bytes = ("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + items + "\"}]}}]}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        os.write(bytes);
        os.close();
    }

    private static long pct(List<Long> values, int p) {
        if (values.isEmpty()) return -1;
        List<Long> sorted = values.stream().sorted().toList();
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
