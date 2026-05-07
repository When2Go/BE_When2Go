package org.example.when2go.domain.reservation.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DayOfWeekSetConverterTest {

    private final DayOfWeekSetConverter converter = new DayOfWeekSetConverter();

    // 반복 요일 집합이 DB 저장 문자열로 변환되는지 확인한다.
    @Test
    void convertToDatabaseColumnConvertsDayOfWeekSetToString() {
        String dbData = converter.convertToDatabaseColumn(Set.of(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY));

        assertThat(dbData).isEqualTo("MONDAY,WEDNESDAY");
    }

    // DB 저장 문자열이 반복 요일 집합으로 변환되는지 확인한다.
    @Test
    void convertToEntityAttributeConvertsStringToDayOfWeekSet() {
        Set<DayOfWeek> repeatDays = converter.convertToEntityAttribute("MONDAY,WEDNESDAY");

        assertThat(repeatDays).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
    }

    // null 또는 빈 반복 요일 값은 null로 변환되는지 확인한다.
    @Test
    void converterReturnsNullForEmptyValues() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToDatabaseColumn(Set.of())).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute(" ")).isNull();
    }
}
