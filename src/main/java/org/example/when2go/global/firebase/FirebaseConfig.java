package org.example.when2go.global.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.example.when2go.domain.notification.client.NotificationPushClient;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(prefix = "notification.fcm", name = "enabled", havingValue = "true")
public class FirebaseConfig {

    @Bean
    FirebaseApp firebaseApp(NotificationProperties properties) {
        try {
            return FirebaseApp.getInstance();
        } catch (IllegalStateException ignored) {
            // default FirebaseApp이 없으면 아래 credentials로 새로 초기화한다.
        }

        try (InputStream credentialsStream = credentialsStream(properties.getFcm())) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                    .build();
            return FirebaseApp.initializeApp(options);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize Firebase credentials", e);
        }
    }

    @Bean
    FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    @Bean
    FirebaseMessageSender firebaseMessageSender(FirebaseMessaging firebaseMessaging) {
        return new FirebaseMessagingSender(firebaseMessaging);
    }

    @Bean
    NotificationPushClient notificationPushClient(FirebaseMessageSender firebaseMessageSender) {
        return new FcmNotificationPushClient(firebaseMessageSender);
    }

    private InputStream credentialsStream(NotificationProperties.Fcm properties) throws IOException {
        if (StringUtils.hasText(properties.getCredentialsPath())) {
            return Files.newInputStream(Path.of(properties.getCredentialsPath()));
        }
        if (StringUtils.hasText(properties.getCredentialsBase64())) {
            byte[] decodedCredentials = Base64.getDecoder().decode(properties.getCredentialsBase64());
            return new ByteArrayInputStream(decodedCredentials);
        }
        throw new IllegalStateException("FCM credentials are required when notification.fcm.enabled=true");
    }
}
