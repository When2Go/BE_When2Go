package org.example.when2go.domain.user.dto;

public record UserExistsResponse(
        boolean exists
) {
    public static UserExistsResponse of(boolean exists) {
        return new UserExistsResponse(exists);
    }
}
