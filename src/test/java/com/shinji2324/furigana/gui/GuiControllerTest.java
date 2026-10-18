package com.shinji2324.furigana.gui;

import com.shinji2324.furigana.core.FuriganaOptions;
import com.shinji2324.furigana.core.FuriganaService;
import com.shinji2324.furigana.furigana.FuriganaDocument;
import com.shinji2324.furigana.mpv.MpvIntegration;
import com.shinji2324.furigana.mpv.ProcessRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GuiController}, with a fake service and a fake MPV runner so
 * that neither the pipeline nor the player is invoked.
 */
class GuiControllerTest {

    private final FakeService service = new FakeService();
    private final RecordingProcessRunner runner = new RecordingProcessRunner();
    private final List<String> messages = new ArrayList<>();
    private final GuiController controller =
            new GuiController(service, new MpvIntegration(runner), messages::add);

    @Test
    @DisplayName("validate reports a missing input file")
    void validateMissingInput() {
        List<String> errors = controller.validate(null, Path.of("out.ass"), null,
                FuriganaOptions.defaults(), false);

        assertTrue(errors.stream().anyMatch(e -> e.contains("input")));
    }

    @Test
    @DisplayName("validate reports a nonexistent input file")
    void validateNonexistentInput(@TempDir Path dir) {
        List<String> errors = controller.validate(dir.resolve("missing.srt"), dir.resolve("out.ass"),
                null, FuriganaOptions.defaults(), false);

        assertTrue(errors.stream().anyMatch(e -> e.contains("not found")));
    }

    @Test
    @DisplayName("validate rejects an output without a supported extension")
    void validateUnsupportedOutput(@TempDir Path dir) throws IOException {
        Path input = Files.writeString(dir.resolve("in.srt"), "1\n00:00:00,000 --> 00:00:01,000\nx\n");

        List<String> errors = controller.validate(input, dir.resolve("out.txt"), null,
                FuriganaOptions.defaults(), false);

        assertTrue(errors.stream().anyMatch(e -> e.contains(".ass or .json")));
    }

    @Test
    @DisplayName("validate requires Embed font when Subset font is set")
    void validateSubsetRequiresEmbed(@TempDir Path dir) throws IOException {
        Path input = Files.writeString(dir.resolve("in.srt"), "1\n00:00:00,000 --> 00:00:01,000\nx\n");
        FuriganaOptions options = new FuriganaOptions(false, false, true, null, null);

        List<String> errors = controller.validate(input, dir.resolve("out.ass"), null, options, false);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Subset font requires")));
    }

    @Test
    @DisplayName("validate requires a video when playing")
    void validateRequiresVideoWhenPlaying(@TempDir Path dir) throws IOException {
        Path input = Files.writeString(dir.resolve("in.srt"), "1\n00:00:00,000 --> 00:00:01,000\nx\n");

        List<String> errors = controller.validate(input, null, null, FuriganaOptions.defaults(), true);

        assertTrue(errors.stream().anyMatch(e -> e.contains("video")));
    }

    @Test
    @DisplayName("validate accepts a complete job")
    void validateAcceptsCompleteJob(@TempDir Path dir) throws IOException {
        Path input = Files.writeString(dir.resolve("in.srt"), "1\n00:00:00,000 --> 00:00:01,000\nx\n");

        assertTrue(controller.validate(input, dir.resolve("out.ass"), null,
                FuriganaOptions.defaults(), false).isEmpty());
    }

    @Test
    @DisplayName("generate writes JSON when the output ends in .json")
    void generateWritesJson(@TempDir Path dir) throws IOException {
        Path input = dir.resolve("in.srt");
        Path output = dir.resolve("out.json");

        controller.generate(input, output, FuriganaOptions.defaults());

        assertEquals("{\"json\":true}", Files.readString(output));
        assertTrue(service.jsonRendered);
        assertFalse(service.assRendered);
    }

    @Test
    @DisplayName("generate writes ASS and forwards the options when the output ends in .ass")
    void generateWritesAss(@TempDir Path dir) throws IOException {
        Path input = dir.resolve("in.srt");
        Path output = dir.resolve("out.ass");
        FuriganaOptions options = new FuriganaOptions(true, true, true, null, "yellow");

        controller.generate(input, output, options);

        assertEquals("[ASS]", Files.readString(output));
        assertTrue(service.assRendered);
        assertEquals(options, service.lastOptions);
    }

    @Test
    @DisplayName("play writes the .furigana.ass next to the SRT and opens MPV")
    void playWritesSubtitleAndOpensMpv(@TempDir Path dir) throws Exception {
        Path subtitle = dir.resolve("episode.srt");
        Path video = dir.resolve("episode.mkv");

        Path ass = controller.play(subtitle, video, FuriganaOptions.defaults());

        assertEquals(dir.resolve("episode.furigana.ass"), ass);
        assertEquals("[ASS]", Files.readString(ass));
        assertEquals(List.of("mpv",
                "--sub-file=" + ass.toAbsolutePath(),
                video.toAbsolutePath().toString()), runner.lastCommand);
    }

    @Test
    @DisplayName("extensionOf returns the lowercase extension without the dot")
    void extensionOfHandlesEdges() {
        assertEquals("ass", GuiController.extensionOf(Path.of("a.ASS")));
        assertEquals("json", GuiController.extensionOf(Path.of("dir", "a.json")));
        assertEquals("", GuiController.extensionOf(Path.of("no-extension")));
        assertEquals("", GuiController.extensionOf(Path.of("trailing.")));
    }

    /**
     * Service stub: returns a canned document and records which renderer was used.
     */
    private static final class FakeService extends FuriganaService {

        private boolean jsonRendered;
        private boolean assRendered;
        private FuriganaOptions lastOptions;

        @Override
        public FuriganaDocument process(Path input, boolean stripParentheses,
                                        Consumer<String> warnings) {
            return new FuriganaDocument();
        }

        @Override
        public String renderJson(FuriganaDocument document) {
            jsonRendered = true;
            return "{\"json\":true}";
        }

        @Override
        public String renderAss(FuriganaDocument document, FuriganaOptions options,
                                Consumer<String> warnings) {
            assRendered = true;
            lastOptions = options;
            return "[ASS]";
        }
    }

    /**
     * Process runner that records the last command instead of opening the player.
     */
    private static final class RecordingProcessRunner implements ProcessRunner {

        private List<String> lastCommand;

        @Override
        public int run(List<String> command) {
            lastCommand = new ArrayList<>(command);
            return 0;
        }
    }
}
