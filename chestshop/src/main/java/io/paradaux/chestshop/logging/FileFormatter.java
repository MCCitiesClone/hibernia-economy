package io.paradaux.chestshop.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * @author Acrobot
 */
public class FileFormatter extends Formatter {
    // DateTimeFormatter is immutable and thread-safe, unlike the shared
    // SimpleDateFormat this replaced (which corrupts output under concurrent
    // logging from async tasks).
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    @Override
    public String format(LogRecord record) {
        StringBuilder message = new StringBuilder(getDateAndTime());

        if (record.getLevel() != Level.INFO) {
            message.append(' ').append(record.getLevel().getLocalizedName());
        }

        message.append(' ').append(record.getMessage());

        if (record.getThrown() != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println();
            record.getThrown().printStackTrace(pw);
            pw.close();
            message.append(sw.toString());
        }

        return message.append('\n').toString();
    }

    private String getDateAndTime() {
        return DATE_FORMAT.format(LocalDateTime.now());
    }
}
