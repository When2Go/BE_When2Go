package org.example.when2go.domain.notification.dto;

import java.util.Map;
import java.util.Objects;

public record PushMessage(
        String token,
        String title,
        String body,
        Map<String, String> data
) {

    public PushMessage {
        Objects.requireNonNull(token, "token must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(body, "body must not be null");
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
