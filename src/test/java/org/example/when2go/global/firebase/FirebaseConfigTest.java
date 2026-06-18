package org.example.when2go.global.firebase;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.when2go.domain.notification.client.NotificationPushClient;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FirebaseConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(NotificationProperties.class)
            .withUserConfiguration(FirebaseConfig.class);

    @Test
    void disabledFcmDoesNotRegisterPushClient() {
        contextRunner
                .withPropertyValues("notification.fcm.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(NotificationPushClient.class));
    }

    @Test
    void enabledFcmWithoutCredentialsFailsFast() {
        contextRunner
                .withPropertyValues("notification.fcm.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
