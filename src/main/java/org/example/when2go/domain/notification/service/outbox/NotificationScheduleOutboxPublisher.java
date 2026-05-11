package org.example.when2go.domain.notification.service.outbox;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.example.when2go.domain.notification.dto.NotificationSqsBatchResult;
import org.example.when2go.domain.notification.dto.NotificationSqsPayload;
import org.example.when2go.domain.notification.entity.NotificationScheduleOutbox;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "notification.sqs", name = "enabled", havingValue = "true")
public class NotificationScheduleOutboxPublisher {

    private final NotificationOutboxClaimService notificationOutboxClaimService;
    private final NotificationOutboxStatusService notificationOutboxStatusService;
    private final NotificationSqsClient notificationSqsClient;
    private final NotificationProperties notificationProperties;

    // claim, markPublished, markFailed 각각이 개별 짧은 트랜잭션으로 동작, SQS 호출을 트랜잭션 안에 넣지 않음
    public int publishPendingOutboxes() {
        List<NotificationScheduleOutbox> outboxes = notificationOutboxClaimService.claimPendingOutboxes(
                notificationProperties.getOutbox().getClaimSize()
        );
        if (outboxes.isEmpty()) {
            return 0;
        }

        int publishedCount = 0;
        int batchSize = Math.min(notificationProperties.getOutbox().getPublishBatchSize(), 10);
        for (int start = 0; start < outboxes.size(); start += batchSize) {
            List<NotificationScheduleOutbox> batch = outboxes.subList(start, Math.min(start + batchSize, outboxes.size()));
            publishedCount += publishBatch(batch);
        }

        return publishedCount;
    }

    private int publishBatch(List<NotificationScheduleOutbox> batch) {
        try {
            NotificationSqsBatchResult result = notificationSqsClient.sendBatch(toPayloads(batch));
            notificationOutboxStatusService.markPublished(result.successIds());
            handleFailures(batch, result.failedIds());
            return result.successIds().size();
        } catch (RuntimeException e) {
            log.warn("event=notification.sqs_publish_failed outboxCount={}", batch.size(), e);
            handleFailures(batch, batch.stream().map(NotificationScheduleOutbox::getId).toList());
            return 0;
        }
    }

    private List<NotificationSqsPayload> toPayloads(List<NotificationScheduleOutbox> outboxes) {
        return outboxes.stream()
                .map(outbox -> new NotificationSqsPayload(
                        outbox.getId(),
                        outbox.getSchedule().getId(),
                        outbox.getTrip().getId(),
                        outbox.getUser().getId(),
                        outbox.getType(),
                        outbox.getTitle(),
                        outbox.getBody(),
                        outbox.getDedupKey()
                ))
                .toList();
    }

    // 실패한 부분에 대해 재시도/최종 실패 처리
    private void handleFailures(List<NotificationScheduleOutbox> batch, List<Long> failedIds) {
        if (failedIds.isEmpty()) {
            return;
        }

        List<Long> retryableIds = new ArrayList<>();
        List<Long> finalFailedIds = new ArrayList<>();

        for (NotificationScheduleOutbox outbox : batch) {
            if (!failedIds.contains(outbox.getId())) {
                continue;
            }

            int nextRetryCount = outbox.getRetryCount() + 1;
            if (nextRetryCount > notificationProperties.getOutbox().getMaxRetry()) {
                finalFailedIds.add(outbox.getId());
            } else {
                retryableIds.add(outbox.getId());
            }
        }

        // 재시도 처리
        notificationOutboxStatusService.markRetryableFailure(retryableIds);

        // 최종 실패 처리
        notificationOutboxStatusService.markFailed(finalFailedIds);
    }
}
