package com.shinji2324.furigana.mpv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link MpvIntegration}, with a fake runner so as not to open the
 * player.
 */
class MpvIntegrationTest {

    private final RecordingProcessRunner runner = new RecordingProcessRunner();
    private final MpvIntegration integration = new MpvIntegration(runner);

    @Test
    @DisplayName("Builds the MPV command with --sub-file and absolute paths")
    void buildsCommandWithAbsolutePaths() {
        Path video = Path.of("videos", "episode.mkv");
        Path subtitle = Path.of("videos", "episode.furigana.ass");

        List<String> command = integration.buildCommand(video, subtitle);

        assertEquals(List.of(
                "mpv",
                "--sub-file=" + subtitle.toAbsolutePath(),
                video.toAbsolutePath().toString()), command);
    }

    @Test
    @DisplayName("open runs the built command and returns the exit code")
    void openRunsBuiltCommand() throws Exception {
        runner.exitCode = 0;
        Path video = Path.of("video.mkv");
        Path subtitle = Path.of("video.furigana.ass");

        int exitCode = integration.open(video, subtitle);

        assertEquals(0, exitCode);
        assertEquals(integration.buildCommand(video, subtitle), runner.lastCommand);
    }

    @Test
    @DisplayName("open returns the nonzero exit code")
    void openPropagatesExitCode() throws Exception {
        runner.exitCode = 3;

        assertEquals(3, integration.open(Path.of("video.mkv"), Path.of("video.furigana.ass")));
    }

    @Test
    @DisplayName("open propagates a process startup error")
    void openPropagatesIoException() {
        runner.failure = new IOException("mpv not found");

        assertThrows(IOException.class,
                () -> integration.open(Path.of("video.mkv"), Path.of("video.furigana.ass")));
    }

    /**
     * Test runner that records the last command and simulates exit code/failure.
     */
    private static final class RecordingProcessRunner implements ProcessRunner {

        private List<String> lastCommand;
        private int exitCode;
        private IOException failure;

        @Override
        public int run(List<String> command) throws IOException {
            lastCommand = new ArrayList<>(command);
            if (failure != null) {
                throw failure;
            }
            return exitCode;
        }
    }
}
