package com.shinji2324.furigana.gui;

import com.shinji2324.furigana.core.FuriganaOptions;
import com.shinji2324.furigana.core.FuriganaService;
import com.shinji2324.furigana.furigana.FuriganaDocument;
import com.shinji2324.furigana.mpv.MpvIntegration;
import com.shinji2324.furigana.mpv.MpvPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * User-interface-independent logic of the graphical interface.
 *
 * <p>Validates a job and runs it through {@link FuriganaService}. It knows nothing
 * about Swing, so it is unit-testable headlessly. The view ({@link MainWindow}) is
 * responsible only for collecting the fields and presenting the messages.
 */
public class GuiController {

    private final FuriganaService service;
    private final MpvIntegration mpvIntegration;
    private final Consumer<String> messages;

    /**
     * Creates the controller.
     *
     * @param service        shared generation facade
     * @param mpvIntegration launches the video in MPV
     * @param messages       sink for the progress and warning messages
     */
    public GuiController(FuriganaService service, MpvIntegration mpvIntegration,
                         Consumer<String> messages) {
        this.service = service;
        this.mpvIntegration = mpvIntegration;
        this.messages = messages;
    }

    /**
     * Validates a job before running it.
     *
     * @param input   input SRT subtitle
     * @param output  output file (ignored when {@code playing})
     * @param video   video file (used when {@code playing})
     * @param options generation options
     * @param playing whether the job opens MPV instead of writing {@code output}
     * @return the list of problems found (empty when the job can run)
     */
    public List<String> validate(Path input, Path output, Path video, FuriganaOptions options,
                                 boolean playing) {
        List<String> errors = new ArrayList<>();
        if (input == null) {
            errors.add("Choose the input SRT file.");
        } else if (!Files.isRegularFile(input)) {
            errors.add("Input file not found: " + input);
        }

        if (playing) {
            if (video == null) {
                errors.add("Choose the video file for MPV.");
            } else if (!Files.isRegularFile(video)) {
                errors.add("Video file not found: " + video);
            }
        } else {
            if (output == null) {
                errors.add("Choose the output file.");
            } else {
                String extension = extensionOf(output);
                if (!extension.equals("ass") && !extension.equals("json")) {
                    errors.add("The output file must end in .ass or .json.");
                }
            }
        }

        if (options.subsetFont() && !options.embedFont()) {
            errors.add("Subset font requires Embed font.");
        }
        return errors;
    }

    /**
     * Runs the pipeline and writes the output file.
     *
     * @param input   input SRT subtitle
     * @param output  output file (.ass or .json)
     * @param options generation options
     * @return the written file
     * @throws IOException if the subtitle or the output cannot be read/written
     */
    public Path generate(Path input, Path output, FuriganaOptions options) throws IOException {
        String rendered = render(input, output, options);
        FuriganaService.writeText(output, rendered);
        return output;
    }

    /**
     * Generates the ASS next to the SRT and opens the video in MPV.
     *
     * @param subtitle input SRT subtitle
     * @param video    video file
     * @param options  generation options
     * @return the generated ASS file
     * @throws IOException          if the subtitle or the ASS cannot be read/written
     * @throws InterruptedException if the MPV process is interrupted
     */
    public Path play(Path subtitle, Path video, FuriganaOptions options)
            throws IOException, InterruptedException {
        Path ass = MpvPaths.furiganaSubtitle(subtitle);
        FuriganaService.writeText(ass, render(subtitle, ass, options));
        mpvIntegration.open(video, ass);
        return ass;
    }

    /**
     * Processes the input and renders it in the format implied by the output
     * extension.
     */
    private String render(Path input, Path output, FuriganaOptions options) throws IOException {
        FuriganaDocument document = service.process(input, options.stripParentheses(), messages);
        if (extensionOf(output).equals("json")) {
            return service.renderJson(document);
        }
        return service.renderAss(document, options, messages);
    }

    /**
     * Returns the path extension in lowercase (without the dot), or an empty
     * string.
     *
     * @param path file path
     * @return lowercase extension without the dot
     */
    static String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
