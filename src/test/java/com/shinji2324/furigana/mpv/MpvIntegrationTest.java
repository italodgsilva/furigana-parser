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
 * Testes do {@link MpvIntegration}, com um runner falso para não abrir o player.
 */
class MpvIntegrationTest {

    private final RecordingProcessRunner runner = new RecordingProcessRunner();
    private final MpvIntegration integration = new MpvIntegration(runner);

    @Test
    @DisplayName("Monta o comando do MPV com --sub-file e caminhos absolutos")
    void buildsCommandWithAbsolutePaths() {
        Path video = Path.of("videos", "episodio.mkv");
        Path subtitle = Path.of("videos", "episodio.furigana.ass");

        List<String> command = integration.buildCommand(video, subtitle);

        assertEquals(List.of(
                "mpv",
                "--sub-file=" + subtitle.toAbsolutePath(),
                video.toAbsolutePath().toString()), command);
    }

    @Test
    @DisplayName("open executa o comando montado e devolve o código de saída")
    void openRunsBuiltCommand() throws Exception {
        runner.exitCode = 0;
        Path video = Path.of("video.mkv");
        Path subtitle = Path.of("video.furigana.ass");

        int exitCode = integration.open(video, subtitle);

        assertEquals(0, exitCode);
        assertEquals(integration.buildCommand(video, subtitle), runner.lastCommand);
    }

    @Test
    @DisplayName("open devolve o código de saída diferente de zero")
    void openPropagatesExitCode() throws Exception {
        runner.exitCode = 3;

        assertEquals(3, integration.open(Path.of("video.mkv"), Path.of("video.furigana.ass")));
    }

    @Test
    @DisplayName("open propaga erro de inicialização do processo")
    void openPropagatesIoException() {
        runner.failure = new IOException("mpv não encontrado");

        assertThrows(IOException.class,
                () -> integration.open(Path.of("video.mkv"), Path.of("video.furigana.ass")));
    }

    /**
     * Runner de teste que registra o último comando e simula código de saída/erro.
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
