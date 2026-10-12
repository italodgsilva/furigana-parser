package com.shinji2324.furigana.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses the command-line arguments.
 *
 * <p>Accepted forms:
 * <pre>
 * furigana-parser [--strip-parentheses] &lt;input.srt&gt; &lt;output.json|output.ass&gt;
 * furigana-parser [--strip-parentheses] --mpv &lt;input.srt&gt; &lt;video&gt;   (alias: play)
 * furigana-parser [--strip-parentheses] [--embed-font[=&lt;file&gt;]] [--subset-font] &lt;input.srt&gt; &lt;output.ass&gt;
 * furigana-parser --help
 * </pre>
 *
 * <p>In generation mode, the output format is decided by the file extension
 * ({@code .json} or {@code .ass}). The {@code --embed-font} option embeds the
 * subtitle font into the ASS file itself, and {@code --subset-font} (which
 * requires the former) subsets the embedded font to the glyphs used (see
 * {@code docs/font-embedding.md}). The {@code --strip-parentheses} option removes
 * the passages between round parentheses from the text before furigana
 * (applies to JSON, ASS and {@code --mpv}).
 */
public class CliParser {

    /** Option that enables font embedding in the ASS. */
    private static final String EMBED_FONT = "--embed-font";

    /** Option that enables subsetting the embedded font to the glyphs used. */
    private static final String SUBSET_FONT = "--subset-font";

    /** Option that removes the passages between round parentheses from the text. */
    private static final String STRIP_PARENTHESES = "--strip-parentheses";

    /** Usage text shown on {@code --help} and on argument errors. */
    public static final String USAGE = String.join("\n",
            "Usage:",
            "  furigana-parser [--strip-parentheses] <input.srt> <output.json|output.ass>",
            "  furigana-parser [--strip-parentheses] --mpv <input.srt> <video>",
            "  furigana-parser [--strip-parentheses] [--embed-font[=<file>]] [--subset-font] "
                    + "<input.srt> <output.ass>",
            "  furigana-parser --help");

    private static final String PLAY_ALIAS = "play";

    /**
     * Converts the arguments into a {@link CliCommand}.
     *
     * @param args command-line arguments
     * @return the parsed command
     * @throws CliUsageException if the arguments do not form a valid command
     */
    public CliCommand parse(String[] args) {
        if (args == null || args.length == 0 || isHelp(args[0])) {
            return CliCommand.help();
        }

        boolean embedFont = false;
        boolean subsetFont = false;
        boolean stripParentheses = false;
        Path fontPath = null;
        List<String> positional = new ArrayList<>();
        for (String argument : args) {
            if (argument.equals(EMBED_FONT)) {
                embedFont = true;
            } else if (argument.equals(SUBSET_FONT)) {
                subsetFont = true;
            } else if (argument.equals(STRIP_PARENTHESES)) {
                stripParentheses = true;
            } else if (argument.startsWith(EMBED_FONT + "=")) {
                embedFont = true;
                String value = argument.substring(EMBED_FONT.length() + 1);
                if (value.isBlank()) {
                    throw new CliUsageException(
                            "Provide the font file as " + EMBED_FONT + "=<file>.\n" + USAGE);
                }
                fontPath = Path.of(value);
            } else {
                positional.add(argument);
            }
        }

        if (positional.isEmpty() || isHelp(positional.get(0))) {
            return CliCommand.help();
        }

        if (subsetFont && !embedFont) {
            throw new CliUsageException(
                    "Option " + SUBSET_FONT + " requires " + EMBED_FONT + ".\n" + USAGE);
        }

        if (isPlay(positional.get(0))) {
            if (positional.size() != 3) {
                throw new CliUsageException(
                        "The --mpv mode requires the SRT subtitle and the video.\n" + USAGE);
            }
            return CliCommand.play(Path.of(positional.get(1)), Path.of(positional.get(2)),
                    embedFont, subsetFont, fontPath, stripParentheses);
        }

        if (positional.size() != 2) {
            throw new CliUsageException(USAGE);
        }

        Path input = Path.of(positional.get(0));
        Path output = Path.of(positional.get(1));
        return switch (extensionOf(output)) {
            case "json" -> CliCommand.generateJson(input, output, stripParentheses);
            case "ass" -> CliCommand.generateAss(input, output, embedFont, subsetFont, fontPath,
                    stripParentheses);
            default -> throw new CliUsageException(
                    "Unsupported output format: use .json or .ass.\n" + USAGE);
        };
    }

    private static boolean isHelp(String argument) {
        return argument.equals("-h") || argument.equals("--help") || argument.equals("help");
    }

    private static boolean isPlay(String argument) {
        return argument.equals("--mpv") || argument.equals(PLAY_ALIAS);
    }

    /**
     * Returns the path extension in lowercase (without the dot), or an empty string.
     */
    private static String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
