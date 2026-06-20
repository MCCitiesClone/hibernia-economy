package io.paradaux.business.utils;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateUtilsTest {

    @Test
    void formatsAsAmericanDate() {
        LocalDateTime dt = LocalDateTime.of(2025, 7, 4, 12, 30);
        assertThat(DateUtils.localDateToAmericanDateString(dt)).isEqualTo("07/04/2025");
    }

    @Test
    void zeroPadsSingleDigitMonthAndDay() {
        LocalDateTime dt = LocalDateTime.of(2025, 1, 9, 0, 0);
        assertThat(DateUtils.localDateToAmericanDateString(dt)).isEqualTo("01/09/2025");
    }

    @Test
    void nullDateThrows() {
        assertThatThrownBy(() -> DateUtils.localDateToAmericanDateString(null))
                .isInstanceOf(NullPointerException.class);
    }
}
