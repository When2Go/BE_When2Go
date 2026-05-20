package org.example.when2go.global.config.notification;

import org.example.when2go.global.aws.sqs.AwsNotificationSqsClient;
import org.example.when2go.domain.notification.client.NotificationSqsClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

    @Bean
    @ConditionalOnProperty(prefix = "notification.sqs", name = "enabled", havingValue = "true")
    SqsClient sqsClient(NotificationProperties properties) {
        return SqsClient.builder()
                .region(Region.of(properties.getSqs().getRegion()))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "notification.sqs", name = "enabled", havingValue = "true")
    NotificationSqsClient notificationSqsClient(
            SqsClient sqsClient,
            NotificationProperties properties
    ) {
        return new AwsNotificationSqsClient(sqsClient, properties.getSqs().getQueueUrl());
    }
}
