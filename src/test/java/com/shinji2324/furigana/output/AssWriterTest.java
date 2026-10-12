package com.shinji2324.furigana.output;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AssWriter} (time and event formatting).
 */
class AssWriterTest {

    private final AssWriter writer = new AssWriter();

    @Test
    @DisplayName("Converts SRT timestamps to the ASS format")
    void convertsSrtTimestamps() {
        assertEquals("0:00:01.00", AssWriter.formatTime("00:00:01,000"));
        assertEquals("0:01:12.50", AssWriter.formatTime("00:01:12,500"));
        assertEquals("1:02:03.40", AssWriter.formatTime("1:02:03,4"));
        assertEquals("0:00:00.90", AssWriter.formatTime("00:00:00.900"));
    }

    @Test
    @DisplayName("Rounds milliseconds and propagates the centisecond carry")
    void roundsAndCarriesCentiseconds() {
        assertEquals("0:00:01.00", AssWriter.formatTime("00:00:00,999"));
        assertEquals("0:01:00.00", AssWriter.formatTime("00:00:59,995"));
    }

    @Test
    @DisplayName("Rejects an invalid timestamp")
    void rejectsInvalidTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> AssWriter.formatTime("00:00:01"));
        assertThrows(IllegalArgumentException.class, () -> AssWriter.formatTime(null));
    }

    @Test
    @DisplayName("Formats an event as a Dialogue line")
    void formatsEventLine() {
        AssEvent event = new AssEvent(0, "0:00:01.00", "0:00:04.50", "Main", "hello");

        assertEquals("Dialogue: 0,0:00:01.00,0:00:04.50,Main,,0,0,0,,hello", writer.formatEvent(event));
    }

    @Test
    @DisplayName("Appends the events to the header, ensuring the final line break")
    void appendsEventsToTemplate() {
        AssTemplate template = new AssTemplate("HEADER\n");
        String output = writer.write(template, List.of(
                new AssEvent(0, "0:00:01.00", "0:00:02.00", "Main", "first"),
                new AssEvent(0, "0:00:01.00", "0:00:02.00", "Furigana", "second")));

        assertEquals("HEADER\n"
                + "Dialogue: 0,0:00:01.00,0:00:02.00,Main,,0,0,0,,first\n"
                + "Dialogue: 0,0:00:01.00,0:00:02.00,Furigana,,0,0,0,,second\n", output);
    }

    @Test
    @DisplayName("Does not emit events when the list is empty")
    void writesTemplateOnlyWithoutEvents() {
        String output = writer.write(new AssTemplate("HEADER\n"), List.of());

        assertEquals("HEADER\n", output);
        assertTrue(writer.write(new AssTemplate("HEADER"), List.of()).startsWith("HEADER"));
    }
}
