package com.shinji2324.furigana.output;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Low-level serializer for the ASS format.
 *
 * <p>Builds the final file by combining the header ({@link AssTemplate}) with the
 * list of {@link AssEvent}, and also converts SRT timestamps to the ASS format. It
 * does not know the {@code FuriganaDocument} or reading sources — that is the
 * responsibility of {@link AssGenerator}.
 */
public class AssWriter {

    /** SRT timestamp: {@code H:MM:SS[,.]mmm}, accepting 1 to 3 fractional digits. */
    private static final Pattern SRT_TIMESTAMP = Pattern.compile(
            "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})");

    /** Fixed line separator, independent of the operating system. */
    private static final String LINE_SEPARATOR = "\n";

    /**
     * Builds the ASS file from the header and the events.
     *
     * @param template header (Script Info, styles and event format)
     * @param events   dialogue events, in display order
     * @return complete ASS content, ending in a newline
     */
    public String write(AssTemplate template, List<AssEvent> events) {
        StringBuilder builder = new StringBuilder(template.content());
        if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
            builder.append(LINE_SEPARATOR);
        }
        for (AssEvent event : events) {
            builder.append(formatEvent(event)).append(LINE_SEPARATOR);
        }
        return builder.toString();
    }

    /**
     * Formats an event as a {@code Dialogue:} line.
     *
     * @param event event to format
     * @return ASS dialogue line
     */
    public String formatEvent(AssEvent event) {
        return "Dialogue: " + event.layer()
                + "," + event.start()
                + "," + event.end()
                + "," + event.style()
                + ",,0,0,0,,"
                + event.text();
    }

    /**
     * Converts an SRT timestamp to the ASS format ({@code H:MM:SS.CC}).
     *
     * <p>Examples: {@code 00:00:01,000} → {@code 0:00:01.00};
     * {@code 00:01:12,500} → {@code 0:01:12.50}. Timestamps with 1 or 3 fractional
     * digits are also accepted (rounded to centiseconds).
     *
     * @param srtTimestamp timestamp in SRT format
     * @return timestamp in ASS format
     * @throws IllegalArgumentException if the timestamp is not recognized
     */
    public static String formatTime(String srtTimestamp) {
        if (srtTimestamp == null) {
            throw new IllegalArgumentException("Null SRT timestamp");
        }
        Matcher matcher = SRT_TIMESTAMP.matcher(srtTimestamp.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid SRT timestamp: " + srtTimestamp);
        }

        int hours = Integer.parseInt(matcher.group(1));
        int minutes = Integer.parseInt(matcher.group(2));
        int seconds = Integer.parseInt(matcher.group(3));
        int centis = toCentiseconds(matcher.group(4));

        if (centis >= 100) {
            centis -= 100;
            seconds++;
            if (seconds >= 60) {
                seconds -= 60;
                minutes++;
                if (minutes >= 60) {
                    minutes -= 60;
                    hours++;
                }
            }
        }

        return String.format("%d:%02d:%02d.%02d", hours, minutes, seconds, centis);
    }

    /**
     * Converts the fractional part (1 to 3 digits) to centiseconds.
     */
    private static int toCentiseconds(String fraction) {
        int value = Integer.parseInt(fraction);
        return switch (fraction.length()) {
            case 1 -> value * 10;
            case 2 -> value;
            default -> Math.round(value / 10.0f);
        };
    }
}
