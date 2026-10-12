package com.shinji2324.furigana.cli;

import java.nio.file.Path;

/**
 * Parsed command-line command.
 *
 * <p>For {@link Mode#GENERATE_JSON} and {@link Mode#GENERATE_ASS}, {@code output}
 * is the output file. For {@link Mode#PLAY}, {@code input} is the SRT subtitle and
 * {@code output} is the video file. For {@link Mode#HELP}, both are
 * {@code null}.
 *
 * <p>{@code embedFont} enables font embedding in the ASS. {@code subsetFont},
 * when enabled, subsets the embedded font to the glyphs used in the subtitle
 * (requires {@code embedFont}). {@code fontPath}, when provided, points to the
 * font file to embed; when {@code null}, the font is located by family name on
 * the system. {@code stripParentheses} removes the passages between round
 * parentheses from the text before furigana (applies to JSON, ASS and
 * {@code --mpv}).
 *
 * @param mode             operation to execute
 * @param input            input SRT subtitle
 * @param output           destination (output file or video), depending on the mode
 * @param embedFont        whether the font should be embedded in the ASS
 * @param subsetFont       whether the embedded font should be subset to the glyphs used
 * @param fontPath         font file to embed, or {@code null} to locate it
 * @param stripParentheses whether the passages between parentheses should be removed
 */
public record CliCommand(Mode mode, Path input, Path output, boolean embedFont, boolean subsetFont,
                         Path fontPath, boolean stripParentheses) {

    /** Operation requested on the command line. */
    public enum Mode {
        /** Show usage instructions. */
        HELP,
        /** Generate the intermediate JSON model. */
        GENERATE_JSON,
        /** Generate the ASS subtitle with furigana. */
        GENERATE_ASS,
        /** Generate the ASS and open the video in MPV. */
        PLAY
    }

    /** Creates the help command. */
    public static CliCommand help() {
        return new CliCommand(Mode.HELP, null, null, false, false, null, false);
    }

    /**
     * Creates the JSON generation command.
     *
     * @param input            SRT subtitle
     * @param output           output JSON file
     * @param stripParentheses whether the passages between parentheses should be removed
     * @return configured command
     */
    public static CliCommand generateJson(Path input, Path output, boolean stripParentheses) {
        return new CliCommand(Mode.GENERATE_JSON, input, output, false, false, null, stripParentheses);
    }

    /**
     * Creates the ASS generation command.
     *
     * @param input            SRT subtitle
     * @param output           output ASS file
     * @param embedFont        whether the font should be embedded in the ASS
     * @param subsetFont       whether the embedded font should be subset to the glyphs used
     * @param fontPath         font file to embed, or {@code null} to locate it
     * @param stripParentheses whether the passages between parentheses should be removed
     * @return configured command
     */
    public static CliCommand generateAss(Path input, Path output, boolean embedFont, boolean subsetFont,
                                         Path fontPath, boolean stripParentheses) {
        return new CliCommand(Mode.GENERATE_ASS, input, output, embedFont, subsetFont, fontPath,
                stripParentheses);
    }

    /**
     * Creates the MPV playback command.
     *
     * @param subtitle         SRT subtitle
     * @param video            video file
     * @param embedFont        whether the font should be embedded in the ASS
     * @param subsetFont       whether the embedded font should be subset to the glyphs used
     * @param fontPath         font file to embed, or {@code null} to locate it
     * @param stripParentheses whether the passages between parentheses should be removed
     * @return configured command
     */
    public static CliCommand play(Path subtitle, Path video, boolean embedFont, boolean subsetFont,
                                  Path fontPath, boolean stripParentheses) {
        return new CliCommand(Mode.PLAY, subtitle, video, embedFont, subsetFont, fontPath,
                stripParentheses);
    }
}
