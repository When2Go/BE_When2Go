package org.example.when2go.domain.trip.service.recalc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.example.when2go.domain.trip.enums.TripRecalcPhase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TripRecalcPhasePolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 12, 9, 0);

    private final TripRecalcPhasePolicy policy = new TripRecalcPhasePolicy();

    // 현재 phase와 남은 시간에 따라 다음 phase가 스펙의 전이표대로 결정되는지 확인한다.
    @ParameterizedTest
    @MethodSource("phaseTransitions")
    void decideNextPhaseFollowsPhaseTransitionTable(
            TripRecalcPhase currentPhase,
            long departureMinutesFromNow,
            TripRecalcPhase expectedPhase
    ) {
        TripRecalcPhase nextPhase = policy.decideNextPhase(
                currentPhase,
                NOW.plusMinutes(departureMinutesFromNow),
                NOW
        );

        assertThat(nextPhase).isEqualTo(expectedPhase);
    }

    // 다음 phase에 따라 다음 재계산 시각이 출발 시각 기준으로 산출되는지 확인한다.
    @ParameterizedTest
    @MethodSource("nextRecalcTimes")
    void computeNextRecalcAtFollowsPhaseSchedule(
            TripRecalcPhase nextPhase,
            long departureMinutesFromNow,
            LocalDateTime expectedNextRecalcAt
    ) {
        LocalDateTime nextRecalcAt = policy.computeNextRecalcAt(
                nextPhase,
                NOW.plusMinutes(departureMinutesFromNow),
                NOW
        );

        assertThat(nextRecalcAt).isEqualTo(expectedNextRecalcAt);
    }

    private static Stream<Arguments> phaseTransitions() {
        return Stream.of(
                Arguments.of(TripRecalcPhase.INITIAL, 60, TripRecalcPhase.PHASE_1),
                Arguments.of(TripRecalcPhase.INITIAL, 59, TripRecalcPhase.PHASE_2),
                Arguments.of(TripRecalcPhase.INITIAL, 30, TripRecalcPhase.PHASE_2),
                Arguments.of(TripRecalcPhase.INITIAL, 29, TripRecalcPhase.PHASE_3),
                Arguments.of(TripRecalcPhase.INITIAL, 20, TripRecalcPhase.PHASE_3),
                Arguments.of(TripRecalcPhase.INITIAL, 19, TripRecalcPhase.PHASE_3),
                Arguments.of(TripRecalcPhase.INITIAL, -1, TripRecalcPhase.PHASE_3),
                Arguments.of(TripRecalcPhase.PHASE_1, 30, TripRecalcPhase.PHASE_2),
                Arguments.of(TripRecalcPhase.PHASE_1, 29, TripRecalcPhase.PHASE_3),
                Arguments.of(TripRecalcPhase.PHASE_1, 20, TripRecalcPhase.PHASE_3),
                Arguments.of(TripRecalcPhase.PHASE_1, 19, TripRecalcPhase.PHASE_3),
                Arguments.of(TripRecalcPhase.PHASE_2, 20, TripRecalcPhase.PHASE_3),
                Arguments.of(TripRecalcPhase.PHASE_2, 19, TripRecalcPhase.PHASE_3),
                Arguments.of(TripRecalcPhase.PHASE_3, 120, TripRecalcPhase.DONE)
        );
    }

    private static Stream<Arguments> nextRecalcTimes() {
        return Stream.of(
                Arguments.of(TripRecalcPhase.PHASE_1, 90, NOW.plusMinutes(30)),
                Arguments.of(TripRecalcPhase.PHASE_2, 45, NOW.plusMinutes(15)),
                Arguments.of(TripRecalcPhase.PHASE_3, 25, NOW.plusMinutes(5)),
                Arguments.of(TripRecalcPhase.PHASE_3, 19, NOW)
        );
    }
}
