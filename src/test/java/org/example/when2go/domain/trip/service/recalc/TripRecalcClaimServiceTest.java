package org.example.when2go.domain.trip.service.recalc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.example.when2go.global.config.trip.TripRecalcProperties;
import org.junit.jupiter.api.Test;

class TripRecalcClaimServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 21, 7);

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripRecalcProperties properties = new TripRecalcProperties();
    private final TripRecalcClaimService service = new TripRecalcClaimService(
            tripRepository,
            properties,
            Clock.fixed(NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"))
    );

    // DB NOW()(RDS 기본 UTC)가 아니라 앱 시계 기준 시각으로 due trip을 조회하고, 같은 기준으로 hold한다.
    @Test
    void claimUsesApplicationClockInsteadOfDatabaseNow() {
        when(tripRepository.claimDueRecalcTripIds(NOW, 10)).thenReturn(List.of(1L, 2L));

        List<Long> claimed = service.claim(10);

        assertThat(claimed).containsExactly(1L, 2L);
        verify(tripRepository).claimDueRecalcTripIds(NOW, 10);
        verify(tripRepository).updateNextRecalcAt(List.of(1L, 2L), NOW.plus(properties.getClaimHoldDuration()));
    }
}
