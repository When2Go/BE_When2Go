package org.example.when2go.global.config.notification;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    private Schedule schedule = new Schedule();
    private Outbox outbox = new Outbox();
    private Sqs sqs = new Sqs();

    @Getter
    @Setter
    public static class Schedule {

        private int claimSize = 500;
        private long fixedDelayMillis = 60_000L;
    }

    @Getter
    @Setter
    public static class Outbox {

        private int claimSize = 500;
        private int publishBatchSize = 10;
        private int maxRetry = 3;
        private long fixedDelayMillis = 60_000L;
        private long recoveryFixedDelayMillis = 60_000L;
        private Duration stuckTimeout = Duration.ofMinutes(5);
    }

    @Getter
    @Setter
    public static class Sqs {

        private boolean enabled = false;
        private String region = "ap-northeast-2";
        private String queueUrl;
    }
}
