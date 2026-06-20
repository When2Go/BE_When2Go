package org.example.when2go.global.dev;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.notification.entity.NotificationSchedule;
import org.example.when2go.domain.notification.entity.NotificationScheduleOutbox;
import org.example.when2go.domain.notification.entity.NotificationType;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.example.when2go.domain.notification.repository.NotificationScheduleRepository;
import org.example.when2go.domain.notification.service.outbox.NotificationOutboxStatusService;
import org.example.when2go.domain.notification.service.schedule.NotificationScheduleBatchService;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.entity.Platform;
import org.example.when2go.domain.user.repository.AppUserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

// release 단순화로 빈 등록 해제됨. 코드 보존용.
@Profile("dev")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "notification.demo", name = "enabled", havingValue = "true")
public class NotificationOutboxDevDemoRunner implements ApplicationRunner {

    private static final Path OUTPUT_PATH = Path.of("build/notification-outbox-dev-demo.md");
    private static final LocalDateTime DEMO_DUE_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final int DEMO_ROW_COUNT = 20;

    private final ConfigurableApplicationContext applicationContext;
    private final AppUserRepository appUserRepository;
    private final TripRepository tripRepository;
    private final NotificationScheduleRepository notificationScheduleRepository;
    private final NotificationScheduleOutboxRepository notificationScheduleOutboxRepository;
    private final NotificationScheduleBatchService notificationScheduleBatchService;
    private final NotificationOutboxStatusService notificationOutboxStatusService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        String demoKey = "portfolio-demo-" + System.currentTimeMillis();

        List<Long> scheduleIds = createDemoSchedules(demoKey);
        int processedCount = processUntilDemoOutboxesCreated(scheduleIds);
        List<Long> outboxIds = findOutboxIds(scheduleIds);
        if (outboxIds.size() != DEMO_ROW_COUNT) {
            throw new IllegalStateException("Expected " + DEMO_ROW_COUNT + " demo outboxes, but got " + outboxIds.size());
        }

        List<Map<String, Object>> afterOutboxCreated = outboxRows(outboxIds);

        markProcessing(outboxIds);
        List<Map<String, Object>> afterFirstClaim = outboxRows(outboxIds);

        notificationOutboxStatusService.markRetryableFailure(outboxIds);
        List<Map<String, Object>> afterSqsFailure = outboxRows(outboxIds);

        markProcessing(outboxIds);
        List<Map<String, Object>> afterRetryClaim = outboxRows(outboxIds);

        writeOutput(
                demoKey,
                processedCount,
                scheduleRows(scheduleIds),
                afterOutboxCreated,
                afterFirstClaim,
                afterSqsFailure,
                afterRetryClaim
        );

        System.out.println();
        System.out.println("Notification outbox dev demo completed.");
        System.out.println("Demo key: " + demoKey);
        System.out.println("Output: " + OUTPUT_PATH.toAbsolutePath());
        System.out.println();

        SpringApplication.exit(applicationContext, () -> 0);
    }

    private List<Long> createDemoSchedules(String demoKey) {
        java.util.ArrayList<Long> scheduleIds = new java.util.ArrayList<>();
        for (int index = 1; index <= DEMO_ROW_COUNT; index++) {
            AppUser user = appUserRepository.save(AppUser.builder()
                    .deviceId(demoKey + "-" + index)
                    .fcmToken("demo-fcm-token-" + demoKey + "-" + index)
                    .platform(Platform.IOS)
                    .build());

            Trip trip = tripRepository.save(Trip.builder()
                    .user(user)
                    .originName("portfolio demo origin " + index)
                    .destName("portfolio demo destination " + index)
                    .originLat(37.1 + index * 0.001)
                    .originLng(127.1 + index * 0.001)
                    .destLat(37.2 + index * 0.001)
                    .destLng(127.2 + index * 0.001)
                    .arrivalTime(LocalDateTime.now().plusHours(1))
                    .routeOption(RouteOption.TRANSIT)
                    .bufferMinutes(10)
                    .finalDepartureTime(DEMO_DUE_TIME.plusSeconds(index))
                    .nextRecalcAt(LocalDateTime.now().plusHours(1))
                    .build());

            NotificationSchedule schedule = notificationScheduleRepository.save(NotificationSchedule.builder()
                    .trip(trip)
                    .user(user)
                    .type(NotificationType.DEPART_NOW)
                    .scheduledAt(DEMO_DUE_TIME.plusSeconds(index))
                    .build());
            scheduleIds.add(schedule.getId());
        }
        return scheduleIds;
    }

    private int processUntilDemoOutboxesCreated(List<Long> scheduleIds) {
        int processedTotal = 0;
        for (int attempt = 0; attempt < 20; attempt++) {
            int processedCount = notificationScheduleBatchService.processDueScheduleBatch(500);
            processedTotal += processedCount;
            if (findOutboxIds(scheduleIds).size() == scheduleIds.size()) {
                return processedTotal;
            }
            if (processedCount == 0) {
                break;
            }
        }
        return processedTotal;
    }

    private List<Long> findOutboxIds(List<Long> scheduleIds) {
        String placeholders = placeholders(scheduleIds.size());
        return jdbcTemplate.queryForList("""
                SELECT id
                FROM notification_schedule_outbox
                WHERE schedule_id IN (%s)
                ORDER BY id ASC
                """.formatted(placeholders), Long.class, scheduleIds.toArray());
    }

    private List<Map<String, Object>> scheduleRows(List<Long> scheduleIds) {
        String placeholders = placeholders(scheduleIds.size());
        return jdbcTemplate.queryForList("""
                SELECT id, user_id, trip_id, type, scheduled_at, status
                FROM notification_schedules
                WHERE id IN (%s)
                ORDER BY id ASC
                """.formatted(placeholders), scheduleIds.toArray());
    }

    private List<Map<String, Object>> outboxRows(List<Long> outboxIds) {
        String placeholders = placeholders(outboxIds.size());
        return jdbcTemplate.queryForList("""
                SELECT id, schedule_id, user_id, trip_id, type, status, retry_count, dedup_key, published_at
                FROM notification_schedule_outbox
                WHERE id IN (%s)
                ORDER BY id ASC
                """.formatted(placeholders), outboxIds.toArray());
    }

    private void markProcessing(List<Long> outboxIds) {
        jdbcTemplate.update("""
                UPDATE notification_schedule_outbox
                SET status = 'PROCESSING',
                    processing_started_at = NOW()
                WHERE id IN (%s)
                """.formatted(placeholders(outboxIds.size())), outboxIds.toArray());
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private void writeOutput(
            String demoKey,
            int processedCount,
            List<Map<String, Object>> schedules,
            List<Map<String, Object>> afterOutboxCreated,
            List<Map<String, Object>> afterFirstClaim,
            List<Map<String, Object>> afterSqsFailure,
            List<Map<String, Object>> afterRetryClaim
    ) throws IOException {
        Files.createDirectories(OUTPUT_PATH.getParent());
        Files.writeString(OUTPUT_PATH, """
                # Notification Outbox Dev DB Demo

                Demo key: `%s`

                ## 1. Due Schedule 처리 결과

                `NotificationScheduleBatchService.processDueScheduleBatch(10)` 실행 결과:

                ```text
                processedCount = %d
                demoScheduleCount = %d
                demoOutboxCount = %d
                ```

                실제 dev DB의 `notification_schedules` rows:

                | id | user_id | trip_id | type | scheduled_at | status |
                |---:|---:|---:|---|---|---|
                %s

                실제 dev DB의 `notification_schedule_outbox` rows:

                | id | schedule_id | user_id | trip_id | type | status | retry_count | dedup_key |
                |---:|---:|---:|---:|---|---|---:|---|
                %s

                ## 2. 실패한 Outbox 재시도 흐름

                SQS 발행 전 claim:

                | id | status | retry_count |
                |---:|---|---:|
                %s

                SQS 발행 실패를 가정해 `markRetryableFailure()` 실행 후:

                | id | status | retry_count |
                |---:|---|---:|
                %s

                다음 publisher 실행에서 다시 claim:

                | id | status | retry_count |
                |---:|---|---:|
                %s

                ## 해석

                - schedule 처리 시 FCM을 직접 호출하지 않고 `notification_schedule_outbox` row %d개가 먼저 생성된다.
                - outbox 생성 후 schedule들은 `DONE`으로 전이된다.
                - SQS 발행 실패 시 outbox는 `PENDING`으로 되돌아가고 `retry_count`가 증가한다.
                - 다음 publisher 실행에서 같은 outbox들이 다시 `PROCESSING`으로 claim되어 재시도 대상이 된다.
                """.formatted(
                demoKey,
                processedCount,
                schedules.size(),
                afterOutboxCreated.size(),
                scheduleTableRows(schedules),
                outboxTableRows(afterOutboxCreated),
                retryTableRows(afterFirstClaim),
                retryTableRows(afterSqsFailure),
                retryTableRows(afterRetryClaim),
                afterOutboxCreated.size()
        ));
    }

    private String scheduleTableRows(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> "| %s | %s | %s | %s | %s | %s |".formatted(
                        row.get("id"),
                        row.get("user_id"),
                        row.get("trip_id"),
                        row.get("type"),
                        row.get("scheduled_at"),
                        row.get("status")
                ))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String outboxTableRows(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> "| %s | %s | %s | %s | %s | %s | %s | %s |".formatted(
                        row.get("id"),
                        row.get("schedule_id"),
                        row.get("user_id"),
                        row.get("trip_id"),
                        row.get("type"),
                        row.get("status"),
                        row.get("retry_count"),
                        row.get("dedup_key")
                ))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String retryTableRows(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(row -> "| %s | %s | %s |".formatted(
                        row.get("id"),
                        row.get("status"),
                        row.get("retry_count")
                ))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }
}
