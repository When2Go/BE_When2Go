package org.example.when2go.domain.user.dto;

public record UserStatusResponse(
        boolean exists
) {
    public static UserStatusResponse of(boolean exists) {
        return new UserStatusResponse(exists);
    }
}
