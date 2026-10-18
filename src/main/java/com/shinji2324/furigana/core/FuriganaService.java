package com.shinji2324.furigana.core;

import com.shinji2324.furigana.font.FontDownloader;
import com.shinji2324.furigana.font.FontFile;
import com.shinji2324.furigana.font.FontLocator;
import com.shinji2324.furigana.font.HarfBuzzSubsetter;
import com.shinji2324.furigana.furigana.FuriganaDocument;
import com.shinji2324.furigana.furigana.UsedCodePoints;
import com.shinji2324.furigana.japanese.JapaneseTokenizer;
import com.shinji2324.furigana.japanese.KuromojiTokenizer;
import com.shinji2324.furigana.output.AssFontEmbedder;
import com.shinji2324.furigana.output.AssGenerator;
import com.shinji2324.furigana.output.AssTemplate;
import com.shinji2324.furigana.output.JsonWriter;
import com.shinji2324.furigana.pipeline.FuriganaPipeline;
import com.shinji2324.furigana.reading.CachingReadingProvider;
import com.shinji2324.furigana.reading.FileReadingCache;
import com.shinji2324.furigana.reading.ReadingProvider;
import com.shinji2324.furigana.reading.jisho.JishoReadingProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Headless facade over the generation pipeline, shared by every user interface
 * (CLI, GUI, tests).
 *
 * <p>Reads the SRT subtitle in UTF-8 and runs the pipeline; readings come from
 * the morphological tokenizer ({@link KuromojiTokenizer}) and, when it does not
 * know a term with kanji, from the {@link JishoReadingProvider} fallback, always
 * mediated by the {@link CachingReadingProvider} (on-disk cache at
 * {@code data/cache/readings.json}). Terms with kanji that remain without a
 * reading generate a warning through the supplied sink; the entry is kept, only
 * without furigana.
 *
 * <p>This class contains no user-interface logic: the caller decides where the
 * messages go and how the result is presented.
 */
public class FuriganaService {

    /** Default location of the reading cache, relative to the working directory. */
    private static final Path DEFAULT_CACHE_FILE = Path.of("data", "cache", "readings.json");

    /**
     * Reads the SRT and runs the complete furigana pipeline.
     *
     * @param input            SRT subtitle
     * @param stripParentheses whether the passages between parentheses should be removed
     * @param warnings         sink for the non-fatal messages of the pipeline
     * @return the parsed furigana document
     * @throws IOException if the subtitle cannot be read
     */
    public FuriganaDocument process(Path input, boolean stripParentheses, Consumer<String> warnings)
            throws IOException {
        String srtContent = Files.readString(input, StandardCharsets.UTF_8);

        JapaneseTokenizer tokenizer = new KuromojiTokenizer();
        ReadingProvider provider = new CachingReadingProvider(
                new JishoReadingProvider(),
                new FileReadingCache(DEFAULT_CACHE_FILE));
        FuriganaPipeline pipeline = new FuriganaPipeline(tokenizer, provider, stripParentheses,
                message -> warnings.accept("Warning: " + message));

        return pipeline.process(srtContent);
    }

    /**
     * Renders the intermediate JSON model.
     *
     * @param document parsed document
     * @return the JSON content
     * @throws IOException if the document cannot be serialized
     */
    public String renderJson(FuriganaDocument document) throws IOException {
        return new JsonWriter().write(document);
    }

    /**
     * Builds the ASS with positioned furigana and, when requested, with the font
     * embedded (and, optionally, subset).
     *
     * @param document parsed document
     * @param options  generation options
     * @param warnings sink for the non-fatal messages (font download, subsetting)
     * @return the ASS content
     * @throws IOException if the font cannot be resolved or embedded
     */
    public String renderAss(FuriganaDocument document, FuriganaOptions options, Consumer<String> warnings)
            throws IOException {
        AssTemplate template = AssTemplate.defaultTemplate();
        if (options.color() != null && !options.color().isBlank()) {
            template = AssTemplate.withColor(template, options.color());
        }
        String ass = new AssGenerator(template).generate(document);
        if (!options.embedFont()) {
            return ass;
        }
        List<FontFile> fonts = resolveFonts(options.fontPath(), new FontDownloader(), template, warnings);
        if (options.subsetFont()) {
            fonts = subsetFonts(fonts, document, warnings);
        }
        return new AssFontEmbedder().embed(ass, fonts);
    }

    /**
     * Writes the content in UTF-8, creating the parent directory when necessary.
     *
     * @param output  destination file
     * @param content text to write
     * @throws IOException if the file cannot be written
     */
    public static void writeText(Path output, String content) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, content, StandardCharsets.UTF_8);
    }

    /**
     * Subsets the fonts to the glyphs used in the subtitle, when HarfBuzz is
     * available.
     *
     * <p>If the native library is not found — or if subsetting a specific font
     * fails — the full font is kept, with a warning. The ASS is never generated
     * without the necessary glyphs.
     */
    private List<FontFile> subsetFonts(List<FontFile> fonts, FuriganaDocument document,
                                       Consumer<String> warnings) {
        Optional<HarfBuzzSubsetter> subsetter = HarfBuzzSubsetter.create();
        if (subsetter.isEmpty()) {
            warnings.accept(HarfBuzzSubsetter.installationHint());
            return fonts;
        }
        Set<Integer> codePoints = UsedCodePoints.from(document);
        List<FontFile> subsetted = new ArrayList<>();
        for (FontFile font : fonts) {
            byte[] reduced = subsetter.get().subset(font.data(), codePoints);
            if (reduced == null) {
                warnings.accept("Warning: could not subset " + font.fileName()
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
    private List<FontFile> resolveFonts(Path fontPath, FontDownloader downloader, AssTemplate template,
                                        Consumer<String> warnings) throws IOException {
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
                    warnings.accept("Font '" + family + "' not installed; using the cached copy in "
                            + downloader.cacheDirectory() + ".");
                } else {
                    warnings.accept("Font '" + family + "' not installed; downloading the official "
                            + FontDownloader.DEFAULT_FAMILY + " (~16 MB, one time) to "
                            + downloader.cacheDirectory() + " ...");
                }
                downloaded = downloader.obtain(family);
                if (downloaded.isPresent()) {
                    warnings.accept(cached
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
}
