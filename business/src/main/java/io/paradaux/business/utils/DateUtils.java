package io.paradaux.business.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtils {

    private static final DateTimeFormatter AMERICAN_DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    public static String localDateToAmericanDateString(LocalDateTime dateTime) {
        return dateTime.format(AMERICAN_DATE_FORMATTER);
    }
}
