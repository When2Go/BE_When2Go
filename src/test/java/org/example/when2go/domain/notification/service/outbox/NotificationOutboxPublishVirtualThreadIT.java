package org.example.when2go.domain.notification.service.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.example.when2go.domain.notification.client.NotificationPushClient;
import org.example.when2go.domain.notification.dto.PushSendResult;
import org.example.when2go.domain.notification.entity.NotificationType;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.entity.Platform;
import org.example.when2go.domain.user.repository.AppUserRepository;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.example.when2go.global.scheduler.NotificationScheduler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 실험 2: 실제 outbox 발송 경로({@link NotificationOutboxPublishService#publishPendingOutboxes()})로
 * PENDING outbox 10만 건을 모두 SENT로 만들 때까지 걸리는 시간을 측정한다.
 * claim(FOR UPDATE SKIP LOCKED) → 발송 → markSent 트랜잭션은 운영 코드 그대로이고,
 * FCM 호출만 200ms 지연을 가진 가짜 클라이언트로 대체한 뒤 발송 실행기와 claim 크기를 바꿔가며 비교한다.
 *
 * <p>실행: VTHREAD_EXPERIMENT=true ./gradlew test --tests "*NotificationOutboxPublishVirtualThreadIT"
 */
@EnabledIfEnvironmentVariable(named = "VTHREAD_EXPERIMENT", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vthread;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.format_sql=false",
        "notification.fcm.enabled=false",
        "logging.level.org.example.when2go=WARN",
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationOutboxPublishVirtualThreadIT {

    private static final int USERS = 1_000;
    private static final int OUTBOXES = 100_000;
    private static final Duration FCM_LATENCY = Duration.ofMillis(200);
    private static final AtomicInteger IN_FLIGHT = new AtomicInteger();
    private static final AtomicInteger MAX_IN_FLIGHT = new AtomicInteger();

    @MockitoBean
    NotificationScheduler notificationScheduler; // 실험 중 스케줄러가 끼어들지 않도록 막는다.

    @Autowired
    NotificationOutboxPublishService notificationOutboxPublishService;
    @Autowired
    NotificationProperties notificationProperties;
    @Autowired
    AppUserRepository appUserRepository;
    @Autowired
    TripRepository tripRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @TestConfiguration
    static class SlowFcmConfig {

        @Bean
        NotificationPushClient slowPushClient() {
            return message -> {
                MAX_IN_FLIGHT.accumulateAndGet(IN_FLIGHT.incrementAndGet(), Math::max);
                try {
                    Thread.sleep(FCM_LATENCY);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    IN_FLIGHT.decrementAndGet();
                }
                return PushSendResult.success("fake");
            };
        }
    }

    @BeforeAll
    void seed() {
        LocalDateTime now = LocalDateTime.now();
        List<AppUser> users = appUserRepository.saveAll(IntStream.range(0, USERS)
                .mapToObj(i -> AppUser.builder()
                        .deviceId("device-" + i)
                        .fcmToken("token-" + i)
                        .platform(Platform.ANDROID)
                        .build())
                .toList());
        List<Trip> trips = tripRepository.saveAll(users.stream()
                .map(user -> Trip.builder()
                        .user(user)
                        .originName("출발지")
                        .destName("도착지")
                        .originLat(37.5).originLng(127.0)
                        .destLat(37.6).destLng(127.1)
                        .arrivalTime(now.plusHours(1))
                        .routeOption(RouteOption.WALK)
                        .bufferMinutes(10)
                        .nextRecalcAt(now.plusHours(1))
                        .build())
                .toList());

        Timestamp createdAt = Timestamp.valueOf(now.minusMinutes(1));
        List<Object[]> schedules = new ArrayList<>(OUTBOXES);
        for (int i = 0; i < OUTBOXES; i++) {
            Trip trip = trips.get(i % trips.size());
            schedules.add(new Object[]{trip.getId(), trip.getUser().getId(), NotificationType.DEPART_NOW.name(),
                    createdAt, "DONE", createdAt, createdAt});
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO notification_schedules
                    (trip_id, user_id, type, scheduled_at, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, schedules);
        jdbcTemplate.update("""
                INSERT INTO notification_schedule_outbox
                    (schedule_id, user_id, trip_id, type, title, body, status, retry_count, dedup_key,
                     created_at, updated_at)
                SELECT id, user_id, trip_id, type, '출발 알림', '지금 출발하세요', 'PENDING', 0,
                       CONCAT('notification:', id), created_at, updated_at
                FROM notification_schedules
                """);
    }

    @Test
    @Order(1)
    void 현행_순차_발송_claim500_1배치() {
        // 10만 건 순차 발송은 5시간 이상 걸리므로 1배치(500건)만 측정해 외삽한다.
        withExecutor(Runnable::run, () -> measure("sequential (현행 for 루프)", 500, 1));
    }

    @Test
    @Order(2)
    void 플랫폼_스레드_200개_claim500() {
        withPlatformPool(() -> measure("platform pool(200)", 500, Integer.MAX_VALUE));
    }

    @Test
    @Order(3)
    void 가상_스레드_claim500() {
        measure("virtual thread-per-task", 500, Integer.MAX_VALUE);
    }

    @Test
    @Order(4)
    void 플랫폼_스레드_200개_claim5000() {
        withPlatformPool(() -> measure("platform pool(200)", 5_000, Integer.MAX_VALUE));
    }

    @Test
    @Order(5)
    void 가상_스레드_claim5000() {
        measure("virtual thread-per-task", 5_000, Integer.MAX_VALUE);
    }

    private void withPlatformPool(Runnable experiment) {
        ExecutorService platformPool = Executors.newFixedThreadPool(200);
        try {
            withExecutor(platformPool, experiment);
        } finally {
            platformPool.shutdown();
        }
    }

    private void withExecutor(Executor executor, Runnable experiment) {
        Object original = ReflectionTestUtils.getField(notificationOutboxPublishService, "notificationPushExecutor");
        ReflectionTestUtils.setField(notificationOutboxPublishService, "notificationPushExecutor", executor);
        try {
            experiment.run();
        } finally {
            ReflectionTestUtils.setField(notificationOutboxPublishService, "notificationPushExecutor", original);
        }
    }

    private void measure(String label, int claimSize, int maxBatches) {
        jdbcTemplate.update("""
                UPDATE notification_schedule_outbox
                SET status = 'PENDING', processing_started_at = NULL, sent_at = NULL
                """);
        int originalClaimSize = notificationProperties.getOutbox().getClaimSize();
        notificationProperties.getOutbox().setClaimSize(claimSize);
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        System.gc();
        threads.resetPeakThreadCount();
        MAX_IN_FLIGHT.set(0);

        int batches = 0;
        long start = System.nanoTime();
        try {
            while (batches < maxBatches && countByStatus("PENDING") > 0) {
                notificationOutboxPublishService.publishPendingOutboxes();
                batches++;
            }
        } finally {
            notificationProperties.getOutbox().setClaimSize(originalClaimSize);
        }
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        int sent = countByStatus("SENT");
        System.out.printf(
                "%n[VTHREAD-OUTBOX] executor=%s claimSize=%,d batches=%,d sent=%,d elapsed=%,dms "
                        + "throughput=%,.0f/s projected100k=%,.1fs maxInFlight=%,d peakPlatformThreads=%,d%n",
                label, claimSize, batches, sent, elapsedMs, sent * 1000.0 / elapsedMs,
                OUTBOXES * (elapsedMs / 1000.0) / sent, MAX_IN_FLIGHT.get(), threads.getPeakThreadCount()
        );
        assertThat(countByStatus("PROCESSING")).isZero();
        if (maxBatches == Integer.MAX_VALUE) {
            assertThat(sent).isEqualTo(OUTBOXES);
        }
    }

    private int countByStatus(String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_schedule_outbox WHERE status = ?", Integer.class, status);
    }
}
