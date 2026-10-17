package com.shinji2324.furigana;

import com.shinji2324.furigana.cli.CliCommand;
import com.shinji2324.furigana.cli.CliParser;
import com.shinji2324.furigana.cli.CliUsageException;
import com.shinji2324.furigana.font.FontDownloader;
import com.shinji2324.furigana.font.FontFile;
import com.shinji2324.furigana.font.FontLocator;
import com.shinji2324.furigana.font.HarfBuzzSubsetter;
import com.shinji2324.furigana.furigana.FuriganaDocument;
import com.shinji2324.furigana.furigana.UsedCodePoints;
import com.shinji2324.furigana.japanese.JapaneseTokenizer;
import com.shinji2324.furigana.japanese.KuromojiTokenizer;
import com.shinji2324.furigana.mpv.MpvIntegration;
import com.shinji2324.furigana.mpv.MpvPaths;
import com.shinji2324.furigana.output.AssFontEmbedder;
import com.shinji2324.furigana.output.AssGenerator;
import com.shinji2324.furigana.output.AssTemplate;
import com.shinji2324.furigana.output.JsonWriter;
import com.shinji2324.furigana.pipeline.FuriganaPipeline;
import com.shinji2324.furigana.reading.CachingReadingProvider;
import com.shinji2324.furigana.reading.FileReadingCache;
import com.shinji2324.furigana.reading.ReadingProvider;
import com.shinji2324.furigana.reading.jisho.JishoReadingProvider;
import com.shinji2324.furigana.subtitle.SubtitleParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
 * <p>Reads the SRT subtitle in UTF-8 and runs the pipeline. Readings come from
 * the morphological tokenizer ({@link KuromojiTokenizer}); when it does not know
 * a term with kanji, the fallback queries the {@link JishoReadingProvider},
 * always mediated by the {@link CachingReadingProvider} (on-disk cache at
 * {@code data/cache/readings.json}). Terms with kanji that remain without a
 * reading — when neither the tokenizer nor Jisho knows them — generate a warning
 * on {@code stderr}; the entry is kept, only without furigana.
 *
 * <p>This class is only the composition point: the logic remains in the pipeline
 * layers. It contains no business rules.
 */
public class Application {

    /** Exit code for success. */
    static final int EXIT_OK = 0;

    /** Exit code for processing error. */
    static final int EXIT_ERROR = 1;

    /** Exit code for argument error. */
    static final int EXIT_USAGE = 2;

    /** Default location of the reading cache, relative to the working directory. */
    private static final Path DEFAULT_CACHE_FILE = Path.of("data", "cache", "readings.json");

    private final CliParser cliParser = new CliParser();
    private final MpvIntegration mpvIntegration = new MpvIntegration();

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
        FuriganaDocument document = process(command.input(), command.stripParentheses());
        String rendered;
        if (ass) {
            rendered = renderAss(document, command);
        } else {
            rendered = new JsonWriter().write(document);
        }
        writeText(command.output(), rendered);
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
        FuriganaDocument document = process(subtitle, command.stripParentheses());
        writeText(ass, renderAss(document, command));
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
     * Builds the ASS with positioned furigana and, when requested, with the font
     * embedded (and, optionally, subset).
     */
    private String renderAss(FuriganaDocument document, CliCommand command) throws IOException {
        AssTemplate template = AssTemplate.defaultTemplate();
        if (command.color() != null && !command.color().isBlank()) {
            template = AssTemplate.withColor(template, command.color());
        }
        String ass = new AssGenerator(template).generate(document);
        if (!command.embedFont()) {
            return ass;
        }
        List<FontFile> fonts = resolveFonts(command.fontPath(), new FontDownloader(), template);
        if (command.subsetFont()) {
            fonts = subsetFonts(fonts, document);
        }
        return new AssFontEmbedder().embed(ass, fonts);
    }

    /**
     * Subsets the fonts to the glyphs used in the subtitle, when HarfBuzz is
     * available.
     *
     * <p>If the native library is not found — or if subsetting a specific font
     * fails — the full font is kept, with a warning on {@code stderr}. The ASS is
     * never generated without the necessary glyphs.
     */
    private List<FontFile> subsetFonts(List<FontFile> fonts, FuriganaDocument document) {
        Optional<HarfBuzzSubsetter> subsetter = HarfBuzzSubsetter.create();
        if (subsetter.isEmpty()) {
            System.err.println(HarfBuzzSubsetter.installationHint());
            return fonts;
        }
        Set<Integer> codePoints = UsedCodePoints.from(document);
        List<FontFile> subsetted = new ArrayList<>();
        for (FontFile font : fonts) {
            byte[] reduced = subsetter.get().subset(font.data(), codePoints);
            if (reduced == null) {
                System.err.println("Warning: could not subset " + font.fileName()
                        + "; embedding the full font.");
                subsetted.add(font);
            } else {
                subsetted.add(new FontFile(font.fileName(), reduced));
            }
        }
        return subsetted;
    }

    /**
     * Resolves the font files declared in the ASS template.
     *
     * <p>With {@code fontPath} provided, embeds that file; otherwise, locates
     * each family on the system.
     */
    private List<FontFile> resolveFonts(Path fontPath, FontDownloader downloader, AssTemplate template) throws IOException {
        List<String> families = (template == null) ? AssTemplate.defaultTemplate().fontFamilies() : template.fontFamilies();
        if (families.isEmpty()) {
            throw new IOException(
                    "The ASS template does not declare any styles with a font to embed.");
        }
        if (fontPath != null) {
            FontLocator locator = new FontLocator();
            return List.of(locator.read(fontPath, families.get(0)));
        }

        FontLocator locator = new FontLocator();
        List<FontFile> fonts = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String family : families) {
            Optional<FontFile> font = locator.find(family);
            if (font.isPresent()) {
                fonts.add(font.get());
            } else {
                missing.add(family);
            }
        }

        // The template font (Noto Sans CJK JP) is not always installed — Windows
        // does not ship it. Instead of failing right away, download the official
        // file once and cache it; guide the user when the download also fails.
        boolean defaultMissing = missing.stream().anyMatch(FontDownloader::isDefaultFamily);
        List<String> stillMissing = new ArrayList<>();
        for (String family : missing) {
            Optional<FontFile> downloaded;
            if (defaultMissing && FontDownloader.isDefaultFamily(family)) {
                boolean cached = downloader.isCached();
                if (cached) {
                    System.err.println("Font '" + family + "' not installed; using the cached copy in "
                            + downloader.cacheDirectory() + ".");
                } else {
                    System.err.println("Font '" + family + "' not installed; downloading the official "
                            + FontDownloader.DEFAULT_FAMILY + " (~16 MB, one time) to "
                            + downloader.cacheDirectory() + " ...");
                }
                downloaded = downloader.obtain(family);
                if (downloaded.isPresent()) {
                    System.err.println(cached
                            ? "Embedded the cached " + FontDownloader.FILE_NAME + "."
                            : "Downloaded and embedded " + FontDownloader.FILE_NAME + ".");
                }
            } else {
                downloaded = Optional.empty();
            }
            if (downloaded.isPresent()) {
                fonts.add(downloaded.get());
            } else {
                stillMissing.add(family);
            }
        }

        if (!stillMissing.isEmpty()) {
            String message = "Font not found on the system: " + String.join(", ", stillMissing) + ".";
            if (defaultMissing) {
                message += " The automatic download failed (check your internet connection "
                        + "and retry). You can download the font from " + FontDownloader.DEFAULT_URL
                        + " and install it (on Windows, double-click the file and choose "
                        + "'Install'), or point to the font file directly with "
                        + "--embed-font=<file>.";
            } else {
                message += " Provide the file with --embed-font=<file>.";
            }
            throw new IOException(message);
        }
        return fonts;
    }

    /**
     * Reads the SRT and runs the complete furigana pipeline.
     */
    private FuriganaDocument process(Path input, boolean stripParentheses) throws IOException {
        String srtContent = Files.readString(input, StandardCharsets.UTF_8);

        JapaneseTokenizer tokenizer = new KuromojiTokenizer();
        ReadingProvider provider = new CachingReadingProvider(
                new JishoReadingProvider(),
                new FileReadingCache(DEFAULT_CACHE_FILE));
        FuriganaPipeline pipeline = new FuriganaPipeline(tokenizer, provider, stripParentheses,
                message -> System.err.println("Warning: " + message));

        return pipeline.process(srtContent);
    }

    /**
     * Writes the content in UTF-8, creating the parent directory when necessary.
     */
    private static void writeText(Path output, String content) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, content, StandardCharsets.UTF_8);
    }
}
