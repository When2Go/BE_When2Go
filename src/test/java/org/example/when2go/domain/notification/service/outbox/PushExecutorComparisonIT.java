package org.example.when2go.domain.notification.service.outbox;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 실험 1: FCM 호출처럼 "대부분 I/O 대기"인 작업을 실행기별로 돌려 처리 시간과 스레드 사용량을 비교한다.
 *
 * <p>실행: VTHREAD_EXPERIMENT=true ./gradlew test --tests "*PushExecutorComparisonIT"
 *
 * <p>- sleep: 작업 1건 = Thread.sleep(200ms)
 * - http: 작업 1건 = 로컬 HTTP 서버(응답 200ms 지연)를 HttpURLConnection으로 호출. 실제 소켓 블로킹에서도 가상 스레드가 unmount되는지 확인용.
 */
@EnabledIfEnvironmentVariable(named = "VTHREAD_EXPERIMENT", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PushExecutorComparisonIT {

    private static final Duration LATENCY = Duration.ofMillis(200);
    private static final int SLEEP_TASKS = 100_000;
    private static final int HTTP_TASKS = 10_000;

    private static HttpServer server;
    private static URI endpoint;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/send", exchange -> {
            sleep(LATENCY);
            byte[] body = "ok".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/send");
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    @Order(1)
    void sleep_현행_플랫폼_스레드_2개() {
        // 10만 건이면 10_000초가 걸리므로 500건만 돌리고 10만 건으로 환산한다.
        run("sleep", "platform pool(2) — 현행", Executors.newFixedThreadPool(2), 500, PushExecutorComparisonIT::sleepTask);
    }

    @Test
    @Order(2)
    void sleep_플랫폼_스레드_200개() {
        run("sleep", "platform pool(200)", Executors.newFixedThreadPool(200), SLEEP_TASKS, PushExecutorComparisonIT::sleepTask);
    }

    @Test
    @Order(3)
    void sleep_가상_스레드() {
        run("sleep", "virtual thread-per-task", Executors.newVirtualThreadPerTaskExecutor(), SLEEP_TASKS,
                PushExecutorComparisonIT::sleepTask);
    }

    @Test
    @Order(4)
    void http_플랫폼_스레드_200개() {
        run("http", "platform pool(200)", Executors.newFixedThreadPool(200), HTTP_TASKS, PushExecutorComparisonIT::httpTask);
    }

    @Test
    @Order(5)
    void http_가상_스레드() {
        run("http", "virtual thread-per-task", Executors.newVirtualThreadPerTaskExecutor(), HTTP_TASKS,
                PushExecutorComparisonIT::httpTask);
    }

    @Test
    @Order(6)
    void sleep_작업마다_플랫폼_스레드_생성() {
        // 가상 스레드처럼 "작업마다 스레드"를 플랫폼 스레드로 하면 OS 스레드 한도에서 실패한다. JVM이 불안정해질 수 있어 마지막에 실행한다.
        run("sleep", "platform thread-per-task", Executors.newThreadPerTaskExecutor(Thread.ofPlatform().factory()),
                SLEEP_TASKS, PushExecutorComparisonIT::sleepTask);
    }

    private void run(String workload, String label, ExecutorService executor, int tasks, Runnable task) {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        threads.resetPeakThreadCount();
        System.gc();
        long heapBefore = usedHeap();

        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        java.util.Map<String, AtomicInteger> failureTypes = new java.util.concurrent.ConcurrentHashMap<>();
        AtomicLong maxHeap = new AtomicLong(heapBefore);
        CountDownLatch done = new CountDownLatch(tasks);
        Thread sampler = Thread.ofPlatform().daemon().start(() -> {
            while (done.getCount() > 0) {
                maxHeap.accumulateAndGet(usedHeap(), Math::max);
                sleep(Duration.ofMillis(20));
            }
        });

        long start = System.nanoTime();
        int submitted = 0;
        String rejected = "-";
        try {
            for (; submitted < tasks; submitted++) {
                executor.execute(() -> {
                    maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                    try {
                        task.run();
                    } catch (RuntimeException e) {
                        failures.incrementAndGet();
                        Throwable cause = e.getCause() == null ? e : e.getCause();
                        failureTypes.computeIfAbsent(cause.getClass().getSimpleName() + ": " + cause.getMessage(),
                                key -> new AtomicInteger()).incrementAndGet();
                    } finally {
                        inFlight.decrementAndGet();
                        done.countDown();
                    }
                });
            }
        } catch (OutOfMemoryError e) {
            rejected = submitted + "번째 작업에서 " + e.getMessage();
            for (int i = submitted; i < tasks; i++) {
                done.countDown();
            }
        }
        await(done);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        sampler.interrupt();
        executor.shutdown();

        int completed = submitted - failures.get();
        System.out.printf(
                "%n[VTHREAD] workload=%s executor=%s tasks=%,d completed=%,d failures=%,d elapsed=%,dms "
                        + "throughput=%,.0f/s projected100k=%,.1fs maxInFlight=%,d peakPlatformThreads=%,d "
                        + "heapDeltaMax=%,dMB rejected=%s failureTypes=%s%n",
                workload, label, tasks, completed, failures.get(), elapsedMs,
                completed * 1000.0 / elapsedMs, 100_000.0 * elapsedMs / completed / 1000,
                maxInFlight.get(), threads.getPeakThreadCount(),
                (maxHeap.get() - heapBefore) / 1024 / 1024, rejected, failureTypes
        );
    }

    private static void sleepTask() {
        sleep(LATENCY);
    }

    private static void httpTask() {
        try {
            HttpURLConnection connection = (HttpURLConnection) endpoint.toURL().openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            try (InputStream in = connection.getInputStream()) {
                in.readAllBytes();
            }
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException("status=" + connection.getResponseCode());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
