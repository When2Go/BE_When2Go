package org.example.when2go.global.config.trip;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "trip.recalc")
public class TripRecalcProperties {

    private int claimSize = 200;
    private long fixedDelayMillis = 60_000L;
    private Duration claimHoldDuration = Duration.ofMinutes(5);
}
