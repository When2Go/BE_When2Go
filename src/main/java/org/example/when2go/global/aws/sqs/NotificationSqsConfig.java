package org.example.when2go.global.aws.sqs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.when2go.domain.notification.client.NotificationQueueClient;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@ConditionalOnProperty(prefix = "notification.sqs", name = "enabled", havingValue = "true")
public class NotificationSqsConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    SqsClient sqsClient(NotificationProperties properties) {
        return SqsClient.builder()
                .region(Region.of(properties.getSqs().getRegion()))
                .build();
    }

    @Bean
    NotificationQueueClient notificationQueueClient(SqsClient sqsClient, NotificationProperties properties) {
        return new NotificationSqsSender(sqsClient, properties.getSqs().getQueueUrl(), objectMapper);
    }
}
