package com.shinji2324.furigana.pipeline;

import com.shinji2324.furigana.furigana.FuriganaDocument;
import com.shinji2324.furigana.furigana.FuriganaEntry;
import com.shinji2324.furigana.furigana.FuriganaSegment;
import com.shinji2324.furigana.furigana.FuriganaSegmenter;
import com.shinji2324.furigana.japanese.JapaneseScript;
import com.shinji2324.furigana.japanese.JapaneseTokenizer;
import com.shinji2324.furigana.japanese.Token;
import com.shinji2324.furigana.reading.Reading;
import com.shinji2324.furigana.reading.ReadingProvider;
import com.shinji2324.furigana.subtitle.ParenthesisStripper;
import com.shinji2324.furigana.subtitle.SrtParser;
import com.shinji2324.furigana.subtitle.Subtitle;
import com.shinji2324.furigana.subtitle.SubtitleEntry;
import com.shinji2324.furigana.subtitle.SubtitleParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Orchestrator of the subtitle processing pipeline.
 *
 * <p>Responsible for connecting the independent stages defined in architecture.md:
 *
 * <pre>
 * SRT → Subtitle Parser → Tokenizer → (optional readings) → FuriganaDocument
 * </pre>
 *
 * <p>For each SRT entry, the pipeline:
 * <ol>
 *   <li>tokenizes the original text;</li>
 *   <li>resolves the reading of each token: the tokenizer's own reading has
 *       priority (Kuromoji) and, when absent, queries the {@link ReadingProvider}
 *       — only for tokens that contain kanji;</li>
 *   <li>splits each token into kanji (with furigana) and okurigana (without
 *       furigana) with the {@link FuriganaSegmenter}, preserving id and
 *       timestamps.</li>
 * </ol>
 *
 * <p>Entries without kanji (only kana/punctuation/spaces) are not discarded: they
 * remain present in the document, with segments without reading. This prevents the
 * subtitle synchronization from being corrupted in the output.
 *
 * <p>Optionally, the text of each entry is filtered with the
 * {@link ParenthesisStripper} before tokenization, removing the passages between
 * parentheses. In this mode, entries that become entirely empty (e.g. a line
 * that was only {@code （笑）}) are discarded — it is the only situation in which
 * an entry ceases to exist.
 *
 * <p>The {@code pipeline} layer does not depend on any specific output format
 * (JSON, ASS, etc.).
 */
public class FuriganaPipeline {

    /** Channel used when no warning is desired. */
    private static final Consumer<String> NO_WARNINGS = message -> { };

    private final SubtitleParser subtitleParser;
    private final JapaneseTokenizer tokenizer;
    private final ReadingProvider readingProvider;
    private final FuriganaSegmenter segmenter;
    private final boolean stripParentheses;
    private final Consumer<String> warningSink;

    /** Surfaces with kanji that had no reading, in order of appearance. */
    private final Set<String> missingReadings = new LinkedHashSet<>();

    /**
     * Creates the pipeline with the default {@link SrtParser}.
     *
     * @param tokenizer       Japanese text tokenizer
     * @param readingProvider reading provider, or {@code null} to process without readings
     */
    public FuriganaPipeline(JapaneseTokenizer tokenizer, ReadingProvider readingProvider) {
        this(tokenizer, readingProvider, false, NO_WARNINGS);
    }

    /**
     * Creates the pipeline with the default {@link SrtParser} and optional cleaning.
     *
     * @param tokenizer        Japanese text tokenizer
     * @param readingProvider  reading provider, or {@code null} to process without readings
     * @param stripParentheses whether the passages between parentheses should be removed from
     *                         the text
     */
    public FuriganaPipeline(JapaneseTokenizer tokenizer, ReadingProvider readingProvider,
                            boolean stripParentheses) {
        this(tokenizer, readingProvider, stripParentheses, NO_WARNINGS);
    }

    /**
     * Creates the pipeline with the default {@link SrtParser}, optional cleaning and a
     * warning channel.
     *
     * @param tokenizer        Japanese text tokenizer
     * @param readingProvider  reading provider, or {@code null} to process without readings
     * @param stripParentheses whether the passages between parentheses should be removed from
     *                         the text
     * @param warningSink      receives warnings about terms that had no reading; may be
     *                         {@code null}
     */
    public FuriganaPipeline(JapaneseTokenizer tokenizer, ReadingProvider readingProvider,
                            boolean stripParentheses, Consumer<String> warningSink) {
        this(new SrtParser(), tokenizer, readingProvider, stripParentheses, warningSink);
    }

    /**
     * Creates the pipeline with a provided subtitle parser.
     *
     * @param subtitleParser  subtitle parser (e.g. {@link SrtParser})
     * @param tokenizer       Japanese text tokenizer
     * @param readingProvider reading provider, or {@code null} to process without readings
     */
    public FuriganaPipeline(SubtitleParser subtitleParser,
                            JapaneseTokenizer tokenizer,
                            ReadingProvider readingProvider) {
        this(subtitleParser, tokenizer, readingProvider, false, NO_WARNINGS);
    }

    /**
     * Creates the pipeline with a provided subtitle parser and optional cleaning.
     *
     * @param subtitleParser   subtitle parser (e.g. {@link SrtParser})
     * @param tokenizer        Japanese text tokenizer
     * @param readingProvider  reading provider, or {@code null} to process without readings
     * @param stripParentheses whether the passages between parentheses should be removed from
     *                         the text
     */
    public FuriganaPipeline(SubtitleParser subtitleParser,
                            JapaneseTokenizer tokenizer,
                            ReadingProvider readingProvider,
                            boolean stripParentheses) {
        this(subtitleParser, tokenizer, readingProvider, stripParentheses, NO_WARNINGS);
    }

    /**
     * Creates the pipeline with a provided subtitle parser, optional cleaning and a
     * warning channel.
     *
     * @param subtitleParser   subtitle parser (e.g. {@link SrtParser})
     * @param tokenizer        Japanese text tokenizer
     * @param readingProvider  reading provider, or {@code null} to process without readings
     * @param stripParentheses whether the passages between parentheses should be removed from
     *                         the text
     * @param warningSink      receives warnings about terms that had no reading; may be
     *                         {@code null}
     */
    public FuriganaPipeline(SubtitleParser subtitleParser,
                            JapaneseTokenizer tokenizer,
                            ReadingProvider readingProvider,
                            boolean stripParentheses,
                            Consumer<String> warningSink) {
        this.subtitleParser = subtitleParser;
        this.tokenizer = tokenizer;
        this.readingProvider = readingProvider;
        this.segmenter = new FuriganaSegmenter();
        this.stripParentheses = stripParentheses;
        this.warningSink = warningSink == null ? NO_WARNINGS : warningSink;
    }

    /**
     * Processes the content of an SRT subtitle.
     *
     * @param srtContent subtitle content (UTF-8)
     * @return intermediate document with the processed entries
     */
    public FuriganaDocument process(String srtContent) {
        return build(subtitleParser.parse(srtContent));
    }

    /**
     * Processes an SRT subtitle file (read as UTF-8).
     *
     * @param srtFile path of the SRT file
     * @return intermediate document with the processed entries
     * @throws IOException if the file cannot be read
     */
    public FuriganaDocument process(Path srtFile) throws IOException {
        return process(Files.readString(srtFile, StandardCharsets.UTF_8));
    }

    /**
     * Converts each subtitle entry into a {@link FuriganaEntry}.
     *
     * <p>When parenthesis cleaning is enabled, the text is filtered before
     * tokenization (avoiding readings for the removed content) and entries that
     * become entirely empty — for example, a line that was only {@code （笑）}
     * — do not enter the document. The others keep their original id and timestamps.
     */
    private FuriganaDocument build(Subtitle subtitle) {
        FuriganaDocument document = new FuriganaDocument();
        missingReadings.clear();
        for (SubtitleEntry entry : subtitle.getEntries()) {
            String text = entry.getText();
            if (stripParentheses) {
                text = ParenthesisStripper.strip(text);
                if (text.isBlank()) {
                    continue;
                }
            }
            document.addEntry(toFuriganaEntry(entry, text));
        }
        warnMissingReadings();
        return document;
    }

    /**
     * Emits one warning per surface with kanji that had no reading.
     *
     * <p>The warning is useful when neither the tokenizer nor the provider knows the
     * term (e.g. proper nouns): the entry is not discarded, but stays without
     * furigana. Repeated surfaces generate a single warning, in order of appearance.
     */
    private void warnMissingReadings() {
        for (String surface : missingReadings) {
            warningSink.accept("no reading for \"" + surface
                    + "\"; the term will stay without furigana.");
        }
    }

    /**
     * Tokenizes the text of an entry, resolves the readings and generates the segments.
     *
     * @param entry original entry (source of id and timestamps)
     * @param text  already cleaned text, which will be tokenized and preserved in the entry
     */
    private FuriganaEntry toFuriganaEntry(SubtitleEntry entry, String text) {
        List<FuriganaSegment> segments = new ArrayList<>();
        for (Token token : tokenizer.tokenize(text)) {
            segments.addAll(segmenter.segment(token.getSurface(), resolveReading(token)));
        }
        return new FuriganaEntry(entry.getId(), entry.getStart(), entry.getEnd(), text, segments);
    }

    /**
     * Determines the reading of a token.
     *
     * <p>The reading provided by the tokenizer (Kuromoji) has priority. Without it,
     * it queries the provider only when the token contains kanji — kana, punctuation
     * and Latin do not need furigana and should not generate external queries.
     *
     * @param token token to resolve
     * @return reading in hiragana, or {@code null} when there is none
     */
    private String resolveReading(Token token) {
        if (token.hasReading()) {
            return token.getReading();
        }
        if (readingProvider == null || !JapaneseScript.containsKanji(token.getSurface())) {
            return null;
        }
        Optional<Reading> reading = readingProvider.findReading(token);
        if (reading.isEmpty()) {
            missingReadings.add(token.getSurface());
        }
        return reading.map(Reading::getReading).orElse(null);
    }
}
