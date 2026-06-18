package org.example.when2go.domain.notification.service.outbox.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.when2go.domain.notification.entity.NotificationOutboxStatus;
import org.example.when2go.domain.notification.entity.NotificationSchedule;
import org.example.when2go.domain.notification.entity.NotificationScheduleOutbox;
import org.example.when2go.domain.notification.entity.NotificationType;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.example.when2go.domain.notification.repository.NotificationScheduleRepository;
import org.example.when2go.domain.notification.service.outbox.NotificationOutboxClaimService;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.entity.Platform;
import org.example.when2go.domain.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox claim 동시성 비교 실험 (SKIP LOCKED 효과 검증).
 *
 * 세 케이스를 같은 데이터/같은 워커 수로 비교한다.
 *  A. 락 없음                 : 여러 워커가 같은 id 를 가져가 중복이 발생해야 한다.
 *  B. FOR UPDATE 만           : 중복은 0 이지만 워커가 락 대기로 직렬화되어 처리 시간이 늘어난다.
 *  C. FOR UPDATE SKIP LOCKED  : 운영 코드. 중복 0 + 워커가 서로 다른 row 를 잡아 병렬 처리한다.
 */
@DisplayName("Outbox claim 동시성 비교 (SKIP LOCKED)")
@org.junit.jupiter.api.Disabled("release 단순화로 비활성화 - outbox 흐름 제거됨")
class OutboxClaimConcurrencyIT extends AbstractOutboxConcurrencyIT {

    private static final int WORKER_COUNT = 15;
    private static final int OUTBOX_COUNT = 5000;
    private static final int CLAIM_LIMIT = 10;

    @Autowired
    private NotificationOutboxClaimService notificationOutboxClaimService;

    @Autowired
    private OutboxClaimTestRepository outboxClaimTestRepository;

    @Autowired
    private NotificationScheduleOutboxRepository outboxRepository;

    @Autowired
    private NotificationScheduleRepository scheduleRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void cleanDatabase() {
        // 매 케이스마다 깨끗한 상태에서 시작
        outboxRepository.deleteAllInBatch();
        scheduleRepository.deleteAllInBatch();
        tripRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("[A] 락 없음 → 중복이 반드시 발생한다")
    void caseA_noLock_producesDuplicates() throws InterruptedException {
        seedPendingOutboxes(OUTBOX_COUNT);

        ExperimentResult result = runWorkers("A. NO LOCK", this::claimNoLock);

        System.out.println(result.format());

        // 락 없으면 같은 row 를 여러 워커가 가져가 중복이 발생해야 한다.
        assertThat(result.duplicateCount)
                .as("락이 없으면 중복 claim 이 발생해야 한다")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("[B] FOR UPDATE 만 → 중복 0, 워커는 직렬화된다")
    void caseB_forUpdate_serializes() throws InterruptedException {
        seedPendingOutboxes(OUTBOX_COUNT);

        ExperimentResult result = runWorkers("B. FOR UPDATE", this::claimForUpdate);

        System.out.println(result.format());

        assertThat(result.duplicateCount)
                .as("FOR UPDATE 는 락 대기로 직렬화되어 중복이 없어야 한다")
                .isZero();
        assertThat(result.uniqueClaimedCount)
                .as("100 건이 모두 처리되어야 한다")
                .isEqualTo(OUTBOX_COUNT);
    }

    @Test
    @DisplayName("[C] FOR UPDATE SKIP LOCKED (운영 코드) → 중복 0 + 병렬 처리")
    void caseC_skipLocked_parallelNoDuplicate() throws InterruptedException {
        seedPendingOutboxes(OUTBOX_COUNT);

        ExperimentResult result = runWorkers(
                "C. FOR UPDATE SKIP LOCKED",
                () -> claimSkipLocked()
        );

        System.out.println(result.format());

        assertThat(result.duplicateCount)
                .as("SKIP LOCKED 는 같은 row 를 두 워커가 못 가져가야 한다")
                .isZero();
        assertThat(result.uniqueClaimedCount)
                .as("100 건이 모두 처리되어야 한다")
                .isEqualTo(OUTBOX_COUNT);
        assertThat(result.activeWorkerCount())
                .as("여러 워커가 실제로 work 를 나눠 가져가야 한다 (병렬성)")
                .isGreaterThan(1);
    }

    // ---------------- 워커 실행 공통 로직 ----------------

    /** 한 워커가 outbox 가 바닥날 때까지 반복 claim → PROCESSING 표시 → 가져간 id 반환. */
    private interface ClaimStrategy {
        List<Long> claimOnce();
    }

    private ExperimentResult runWorkers(String caseName, ClaimStrategy strategy) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(WORKER_COUNT);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(WORKER_COUNT);

        // 워커별로 잡은 id 누적
        List<List<Long>> perWorker = new ArrayList<>();
        for (int i = 0; i < WORKER_COUNT; i++) {
            perWorker.add(Collections.synchronizedList(new ArrayList<>()));
        }
        AtomicInteger emptyHits = new AtomicInteger();

        long startNanos;
        for (int i = 0; i < WORKER_COUNT; i++) {
            final int workerIdx = i;
            pool.submit(() -> {
                try {
                    startGate.await();
                    while (true) {
                        List<Long> claimed = strategy.claimOnce();
                        if (claimed.isEmpty()) {
                            // 두 번 연속 비면 종료 (다른 워커가 잡고 있을 수도 있으니 한 번은 더 시도)
                            if (emptyHits.incrementAndGet() >= WORKER_COUNT * 2) {
                                return;
                            }
                            // 잠깐 양보
                            Thread.sleep(5);
                            continue;
                        }
                        emptyHits.set(0);
                        perWorker.get(workerIdx).addAll(claimed);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startNanos = System.nanoTime();
        startGate.countDown(); // 동시 출발
        boolean finished = doneGate.await(60, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        pool.shutdownNow();

        if (!finished) {
            throw new IllegalStateException("실험이 60초 안에 끝나지 않았습니다 — 데드락/무한대기 의심");
        }

        // 결과 집계
        ConcurrentHashMap<Long, Integer> idToClaimCount = new ConcurrentHashMap<>();
        int[] perWorkerCount = new int[WORKER_COUNT];
        for (int i = 0; i < WORKER_COUNT; i++) {
            List<Long> ids = perWorker.get(i);
            perWorkerCount[i] = ids.size();
            for (Long id : ids) {
                idToClaimCount.merge(id, 1, Integer::sum);
            }
        }
        int duplicates = 0;
        for (int v : idToClaimCount.values()) {
            if (v > 1) {
                duplicates += (v - 1);
            }
        }
        return new ExperimentResult(
                caseName,
                idToClaimCount.size(),
                duplicates,
                perWorkerCount,
                elapsedMs
        );
    }

    // ---------------- 케이스별 claim 구현 ----------------

    /** A. 락 없음 — 별도 트랜잭션에서 SELECT + UPDATE PROCESSING. */
    private List<Long> claimNoLock() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(TransactionTemplate.ISOLATION_READ_COMMITTED);
        return tx.execute(status -> {
            List<Long> ids = outboxClaimTestRepository.selectPendingIdsNoLock(CLAIM_LIMIT);
            if (ids.isEmpty()) {
                return List.<Long>of();
            }
            outboxRepository.updateProcessing(ids, NotificationOutboxStatus.PROCESSING, LocalDateTime.now());
            return ids;
        });
    }

    /** B. FOR UPDATE — SKIP LOCKED 없이 락만. 워커들이 줄 서서 처리. */
    private List<Long> claimForUpdate() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(TransactionTemplate.ISOLATION_READ_COMMITTED);
        return tx.execute(status -> {
            List<Long> ids = outboxClaimTestRepository.selectPendingIdsForUpdate(CLAIM_LIMIT);
            if (ids.isEmpty()) {
                return List.<Long>of();
            }
            outboxRepository.updateProcessing(ids, NotificationOutboxStatus.PROCESSING, LocalDateTime.now());
            return ids;
        });
    }

    /** C. 운영 코드 그대로 — FOR UPDATE SKIP LOCKED. */
    private List<Long> claimSkipLocked() {
        List<NotificationScheduleOutbox> claimed =
                notificationOutboxClaimService.claimPendingOutboxes(CLAIM_LIMIT);
        return claimed.stream().map(NotificationScheduleOutbox::getId).toList();
    }

    // ---------------- 시드 ----------------

    private void seedPendingOutboxes(int count) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (int i = 0; i < count; i++) {
                AppUser user = AppUser.builder()
                        .deviceId("device-" + i)
                        .fcmToken("token-" + i)
                        .platform(Platform.IOS)
                        .build();
                appUserRepository.save(user);

                Trip trip = Trip.builder()
                        .user(user)
                        .originName("home")
                        .destName("office")
                        .originLat(37.1)
                        .originLng(127.1)
                        .destLat(37.2)
                        .destLng(127.2)
                        .arrivalTime(LocalDateTime.of(2026, 5, 7, 10, 0))
                        .routeOption(RouteOption.TRANSIT)
                        .bufferMinutes(10)
                        .finalDepartureTime(LocalDateTime.of(2026, 5, 7, 9, 0))
                        .nextRecalcAt(LocalDateTime.of(2026, 5, 7, 8, 0))
                        .build();
                tripRepository.save(trip);

                NotificationSchedule schedule = NotificationSchedule.builder()
                        .trip(trip)
                        .user(user)
                        .type(NotificationType.DEPART_NOW)
                        .scheduledAt(LocalDateTime.of(2026, 5, 7, 9, 0))
                        .build();
                scheduleRepository.save(schedule);

                NotificationScheduleOutbox outbox = NotificationScheduleOutbox.builder()
                        .schedule(schedule)
                        .user(user)
                        .trip(trip)
                        .type(NotificationType.DEPART_NOW)
                        .title("title-" + i)
                        .body("body-" + i)
                        .dedupKey("notification:" + i)
                        .build();
                outboxRepository.save(outbox);
            }
        });
    }

    // ---------------- 결과 ----------------

    private record ExperimentResult(
            String caseName,
            int uniqueClaimedCount,
            int duplicateCount,
            int[] perWorkerCount,
            long elapsedMs
    ) {
        int activeWorkerCount() {
            int n = 0;
            for (int c : perWorkerCount) {
                if (c > 0) n++;
            }
            return n;
        }

        String format() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n========================================================\n");
            sb.append("[").append(caseName).append("]\n");
            sb.append("  unique claimed       : ").append(uniqueClaimedCount).append(" / ").append(OUTBOX_COUNT).append("\n");
            sb.append("  duplicate claims     : ").append(duplicateCount).append("\n");
            sb.append("  active workers       : ").append(activeWorkerCount()).append(" / ").append(WORKER_COUNT).append("\n");
            sb.append("  elapsed              : ").append(elapsedMs).append(" ms\n");
            sb.append("  per-worker counts    : ");
            for (int c : perWorkerCount) {
                sb.append(c).append(" ");
            }
            sb.append("\n========================================================\n");
            return sb.toString();
        }
    }
}
