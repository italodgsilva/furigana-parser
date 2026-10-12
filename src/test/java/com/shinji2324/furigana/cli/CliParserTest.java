package com.shinji2324.furigana.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CliParser}.
 */
class CliParserTest {

    private final CliParser parser = new CliParser();

    @Test
    @DisplayName("With no arguments or with help, returns HELP mode")
    void returnsHelpMode() {
        assertEquals(CliCommand.Mode.HELP, parser.parse(new String[] {}).mode());
        assertEquals(CliCommand.Mode.HELP, parser.parse(new String[] {"--help"}).mode());
        assertEquals(CliCommand.Mode.HELP, parser.parse(new String[] {"-h"}).mode());
        assertEquals(CliCommand.Mode.HELP, parser.parse(new String[] {"help"}).mode());
        assertNull(parser.parse(new String[] {}).input());
    }

    @Test
    @DisplayName("Parses JSON generation")
    void parsesJsonGeneration() {
        CliCommand command = parser.parse(new String[] {"input.srt", "output.json"});

        assertEquals(CliCommand.Mode.GENERATE_JSON, command.mode());
        assertEquals(Path.of("input.srt"), command.input());
        assertEquals(Path.of("output.json"), command.output());
    }

    @Test
    @DisplayName("Parses ASS generation (case-insensitive extension)")
    void parsesAssGeneration() {
        CliCommand command = parser.parse(new String[] {"input.srt", "OUTPUT.ASS"});

        assertEquals(CliCommand.Mode.GENERATE_ASS, command.mode());
        assertEquals(Path.of("OUTPUT.ASS"), command.output());
    }

    @Test
    @DisplayName("Parses the --mpv playback mode and the play alias")
    void parsesPlayMode() {
        CliCommand command = parser.parse(new String[] {"--mpv", "subtitle.srt", "video.mkv"});
        assertEquals(CliCommand.Mode.PLAY, command.mode());
        assertEquals(Path.of("subtitle.srt"), command.input());
        assertEquals(Path.of("video.mkv"), command.output());

        assertEquals(CliCommand.Mode.PLAY,
                parser.parse(new String[] {"play", "subtitle.srt", "video.mkv"}).mode());
    }

    @Test
    @DisplayName("Rejects unsupported output extension")
    void rejectsUnsupportedExtension() {
        assertThrows(CliUsageException.class,
                () -> parser.parse(new String[] {"input.srt", "output.txt"}));
    }

    @Test
    @DisplayName("Rejects invalid argument count")
    void rejectsWrongArgumentCount() {
        assertThrows(CliUsageException.class,
                () -> parser.parse(new String[] {"input.srt"}));
        assertThrows(CliUsageException.class,
                () -> parser.parse(new String[] {"a", "b", "c"}));
        assertThrows(CliUsageException.class,
                () -> parser.parse(new String[] {"--mpv", "subtitle.srt"}));
    }

    @Test
    @DisplayName("Parses font embedding in ASS generation")
    void parsesEmbedFont() {
        CliCommand auto = parser.parse(new String[] {"--embed-font", "input.srt", "output.ass"});
        assertEquals(CliCommand.Mode.GENERATE_ASS, auto.mode());
        assertTrue(auto.embedFont());
        assertNull(auto.fontPath());

        CliCommand explicit = parser.parse(new String[] {
                "--embed-font=/tmp/font.otf", "input.srt", "output.ass"});
        assertTrue(explicit.embedFont());
        assertEquals(Path.of("/tmp/font.otf"), explicit.fontPath());
    }

    @Test
    @DisplayName("Font embedding also applies to --mpv mode")
    void parsesEmbedFontInPlayMode() {
        CliCommand command = parser.parse(new String[] {
                "--embed-font", "--mpv", "subtitle.srt", "video.mkv"});

        assertEquals(CliCommand.Mode.PLAY, command.mode());
        assertTrue(command.embedFont());
    }

    @Test
    @DisplayName("Ignores font embedding in JSON generation")
    void ignoresEmbedFontForJson() {
        CliCommand command = parser.parse(new String[] {
                "--embed-font", "input.srt", "output.json"});

        assertEquals(CliCommand.Mode.GENERATE_JSON, command.mode());
        assertFalse(command.embedFont());
    }

    @Test
    @DisplayName("Rejects --embed-font= without a file")
    void rejectsEmptyEmbedFontPath() {
        assertThrows(CliUsageException.class,
                () -> parser.parse(new String[] {"--embed-font=", "input.srt", "output.ass"}));
    }

    @Test
    @DisplayName("Parses --subset-font together with --embed-font")
    void parsesSubsetFont() {
        CliCommand command = parser.parse(new String[] {
                "--embed-font", "--subset-font", "input.srt", "output.ass"});

        assertEquals(CliCommand.Mode.GENERATE_ASS, command.mode());
        assertTrue(command.embedFont());
        assertTrue(command.subsetFont());

        CliCommand plain = parser.parse(new String[] {"--embed-font", "input.srt", "output.ass"});
        assertFalse(plain.subsetFont());
    }

    @Test
    @DisplayName("--subset-font also applies in --mpv mode")
    void parsesSubsetFontInPlayMode() {
        CliCommand command = parser.parse(new String[] {
                "--embed-font", "--subset-font", "--mpv", "subtitle.srt", "video.mkv"});

        assertEquals(CliCommand.Mode.PLAY, command.mode());
        assertTrue(command.subsetFont());
    }

    @Test
    @DisplayName("Rejects --subset-font without --embed-font")
    void rejectsSubsetFontWithoutEmbed() {
        assertThrows(CliUsageException.class,
                () -> parser.parse(new String[] {"--subset-font", "input.srt", "output.ass"}));
    }

    @Test
    @DisplayName("Parses --strip-parentheses in ASS, JSON and --mpv")
    void parsesStripParentheses() {
        assertTrue(parser.parse(new String[] {
                "--strip-parentheses", "input.srt", "output.ass"}).stripParentheses());
        assertTrue(parser.parse(new String[] {
                "--strip-parentheses", "input.srt", "output.json"}).stripParentheses());
        assertTrue(parser.parse(new String[] {
                "--strip-parentheses", "--mpv", "subtitle.srt", "video.mkv"}).stripParentheses());

        assertFalse(parser.parse(new String[] {"input.srt", "output.ass"}).stripParentheses());
    }
}
