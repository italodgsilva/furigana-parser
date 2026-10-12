package com.shinji2324.furigana.mpv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Integration with MPV for playing a video with the furigana subtitle.
 *
 * <p>This layer is deliberately simple and decoupled from the pipeline: it only
 * builds and runs the command
 * {@code mpv --sub-file=<subtitle.ass> <video>}. It knows neither SRT, Kuromoji,
 * Jisho nor the {@code FuriganaDocument}. The path of the generated subtitle is
 * the caller's responsibility (see {@link MpvPaths}).
 *
 * <p>The actual execution of the process is delegated to a {@link ProcessRunner},
 * which makes it possible to test the integration without opening the player.
 *
 * <p>It assumes that the {@code mpv} executable is installed and available on
 * the {@code PATH}.
 */
public class MpvIntegration {

    /** Name of the MPV executable searched for on the {@code PATH}. */
    public static final String MPV_EXECUTABLE = "mpv";

    private final ProcessRunner processRunner;

    /** Creates the integration with the default process runner. */
    public MpvIntegration() {
        this(new DefaultProcessRunner());
    }

    /**
     * Creates the integration with a given process runner.
     *
     * @param processRunner runner used to start MPV
     */
    public MpvIntegration(ProcessRunner processRunner) {
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
    }

    /**
     * Builds the MPV command that opens the video with the furigana subtitle.
     *
     * @param video         video file
     * @param subtitleFile  ASS file with the furigana
     * @return command line ready for execution
     */
    public List<String> buildCommand(Path video, Path subtitleFile) {
        Objects.requireNonNull(video, "video");
        Objects.requireNonNull(subtitleFile, "subtitleFile");
        return List.of(
                MPV_EXECUTABLE,
                "--sub-file=" + subtitleFile.toAbsolutePath(),
                video.toAbsolutePath().toString());
    }

    /**
     * Opens the video in MPV with the furigana subtitle and waits for it to
     * finish.
     *
     * @param video        video file
     * @param subtitleFile ASS file with the furigana
     * @return MPV exit code
     * @throws IOException          if MPV cannot be started (e.g. not installed)
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public int open(Path video, Path subtitleFile) throws IOException, InterruptedException {
        return processRunner.run(buildCommand(video, subtitleFile));
    }
}
