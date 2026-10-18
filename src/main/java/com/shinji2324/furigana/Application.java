package com.shinji2324.furigana;

import com.shinji2324.furigana.cli.CliCommand;
import com.shinji2324.furigana.cli.CliParser;
import com.shinji2324.furigana.cli.CliUsageException;
import com.shinji2324.furigana.core.FuriganaOptions;
import com.shinji2324.furigana.core.FuriganaService;
import com.shinji2324.furigana.furigana.FuriganaDocument;
import com.shinji2324.furigana.mpv.MpvIntegration;
import com.shinji2324.furigana.mpv.MpvPaths;
import com.shinji2324.furigana.subtitle.SubtitleParseException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Application entry point (CLI).
 *
 * <p>Usage forms:
 * <pre>
 * furigana-parser &lt;input.srt&gt; &lt;output.json|output.ass&gt;
 * furigana-parser --mpv &lt;input.srt&gt; &lt;video&gt;
 * furigana-parser --help
 * </pre>
 *
 * <p>The output format is chosen by the output file extension:
 * {@code .json} generates the intermediate model and {@code .ass} generates the
 * subtitle with positioned furigana.
 *
 * <p>In {@code --mpv} mode, the subtitle is generated next to the SRT (suffix
 * {@code .furigana.ass}) and the video is opened in MPV with
 * {@code --sub-file}. See the limitations in {@code docs/mpv.md}.
 *
 * <p>This class is only the composition point: the logic lives in
 * {@link FuriganaService} and the pipeline layers. It contains no business rules.
 */
public class Application {

    /** Exit code for success. */
    static final int EXIT_OK = 0;

    /** Exit code for processing error. */
    static final int EXIT_ERROR = 1;

    /** Exit code for argument error. */
    static final int EXIT_USAGE = 2;

    private final CliParser cliParser = new CliParser();
    private final MpvIntegration mpvIntegration = new MpvIntegration();
    private final FuriganaService service = new FuriganaService();

    /**
     * CLI entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        int exitCode = new Application().run(args);
        if (exitCode != EXIT_OK) {
            System.exit(exitCode);
        }
    }

    /**
     * Runs the application and returns the exit code.
     *
     * <p>Separated from {@link #main(String[])} so as not to terminate the JVM
     * when used in tests.
     *
     * @param args command-line arguments
     * @return exit code
     */
    public int run(String[] args) {
        CliCommand command;
        try {
            command = cliParser.parse(args);
        } catch (CliUsageException e) {
            System.err.println(e.getMessage());
            return EXIT_USAGE;
        }

        try {
            return execute(command);
        } catch (SubtitleParseException e) {
            System.err.println("Invalid subtitle: " + e.getMessage());
            return EXIT_ERROR;
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid output: " + e.getMessage());
            return EXIT_ERROR;
        } catch (IOException e) {
            // JsonProcessingException is a subclass of IOException, so it is covered here.
            System.err.println("Error processing subtitle: " + e.getMessage());
            return EXIT_ERROR;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Execution interrupted: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    /**
     * Dispatches the parsed command.
     */
    private int execute(CliCommand command) throws IOException, InterruptedException {
        return switch (command.mode()) {
            case HELP -> {
                System.out.println(CliParser.USAGE);
                yield EXIT_OK;
            }
            case GENERATE_JSON -> generate(command, false);
            case GENERATE_ASS -> generate(command, true);
            case PLAY -> play(command);
        };
    }

    /**
     * Generates the requested output (JSON or ASS) to the given file.
     */
    private int generate(CliCommand command, boolean ass) throws IOException {
        FuriganaDocument document = service.process(command.input(), command.stripParentheses(), warningSink());
        String rendered = ass
                ? service.renderAss(document, optionsOf(command), warningSink())
                : service.renderJson(document);
        FuriganaService.writeText(command.output(), rendered);
        System.out.println("Furigana generated (" + (ass ? "ass" : "json") + "): "
                + command.output().toAbsolutePath());
        return EXIT_OK;
    }

    /**
     * Generates the ASS next to the SRT and opens the video in MPV.
     */
    private int play(CliCommand command) throws IOException, InterruptedException {
        Path subtitle = command.input();
        Path video = command.output();
        Path ass = MpvPaths.furiganaSubtitle(subtitle);
        FuriganaDocument document = service.process(subtitle, command.stripParentheses(), warningSink());
        FuriganaService.writeText(ass, service.renderAss(document, optionsOf(command), warningSink()));
        System.out.println("Subtitle generated: " + ass.toAbsolutePath());
        System.out.println("Opening MPV: " + video.toAbsolutePath());

        try {
            int exitCode = mpvIntegration.open(video, ass);
            if (exitCode != 0) {
                System.err.println("MPV exited with code " + exitCode);
                return EXIT_ERROR;
            }
            return EXIT_OK;
        } catch (IOException e) {
            System.err.println("Failed to start MPV (make sure 'mpv' is installed and on the PATH): "
                    + e.getMessage());
            return EXIT_ERROR;
        }
    }

    /**
     * Maps the parsed CLI command onto the interface-independent options.
     */
    private static FuriganaOptions optionsOf(CliCommand command) {
        return new FuriganaOptions(command.stripParentheses(), command.embedFont(), command.subsetFont(),
                command.fontPath(), command.color());
    }

    /**
     * Sink for the non-fatal messages, printed on {@code stderr}.
     */
    private static Consumer<String> warningSink() {
        return System.err::println;
    }
}
