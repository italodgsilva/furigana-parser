package com.shinji2324.furigana.pipeline;

import com.shinji2324.furigana.furigana.FuriganaDocument;
import com.shinji2324.furigana.furigana.FuriganaEntry;
import com.shinji2324.furigana.furigana.FuriganaSegment;
import com.shinji2324.furigana.japanese.JapaneseTokenizer;
import com.shinji2324.furigana.japanese.KuromojiTokenizer;
import com.shinji2324.furigana.japanese.ScriptJapaneseTokenizer;
import com.shinji2324.furigana.japanese.Token;
import com.shinji2324.furigana.reading.Reading;
import com.shinji2324.furigana.reading.ReadingProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FuriganaPipeline}.
 */
class FuriganaPipelineTest {

    @Test
    @DisplayName("Preserves id, timestamps and original text of each entry")
    void preservesEntryMetadata() {
        FuriganaPipeline pipeline = pipelineWithoutProvider();

        FuriganaDocument document = pipeline.process("""
                1
                00:00:01,000 --> 00:00:04,500
                今日は日本語を勉強します。

                2
                00:00:05,000 --> 00:00:08,000
                元気ですか？
                """);

        assertEquals(2, document.size());

        FuriganaEntry first = document.getEntries().get(0);
        assertEquals("1", first.id());
        assertEquals("00:00:01,000", first.start());
        assertEquals("00:00:04,500", first.end());
        assertEquals("今日は日本語を勉強します。", first.text());

        FuriganaEntry second = document.getEntries().get(1);
        assertEquals("元気ですか？", second.text());
        assertTrue(second.segments().stream().allMatch(segment -> segment.reading() == null));
    }

    @Test
    @DisplayName("Generates one segment per token, with null reading when there is no provider")
    void createsOneSegmentPerTokenWithoutProvider() {
        FuriganaDocument document = pipelineWithoutProvider()
                .process("1\n00:00:01,000 --> 00:00:02,000\n今日は日本語。");

        FuriganaEntry entry = document.getEntries().get(0);

        assertEquals(List.of("今日", "は", "日本語", "。"),
                entry.segments().stream().map(FuriganaSegment::text).toList());
        assertTrue(entry.segments().stream().allMatch(segment -> segment.reading() == null));
    }

    @Test
    @DisplayName("Uses the reading provider when configured")
    void usesReadingProviderWhenConfigured() {
        ReadingProvider provider = token -> {
            if (token.getSurface().equals("日本語")) {
                return Optional.of(new Reading("日本語", "にほんご", "test"));
            }
            return Optional.empty();
        };

        List<FuriganaSegment> segments = new FuriganaPipeline(new ScriptJapaneseTokenizer(), provider)
                .process("1\n00:00:01,000 --> 00:00:02,000\n日本語を勉強。")
                .getEntries().get(0).segments();

        assertEquals("にほんご", segments.get(0).reading());
        assertNull(segments.get(1).reading()); // を, no reading
        assertEquals("勉強", segments.get(2).text());
        assertNull(segments.get(2).reading());
    }

    @Test
    @DisplayName("Keeps entries without kanji (does not discard, avoids desynchronization)")
    void keepsEntriesWithoutKanji() {
        FuriganaDocument document = pipelineWithoutProvider()
                .process("1\n00:00:01,000 --> 00:00:02,000\nありがとうございます。");

        assertEquals(1, document.size());
        assertEquals("ありがとうございます。", document.getEntries().get(0).text());
    }

    @Test
    @DisplayName("Preserves internal line breaks in the entry text")
    void preservesInternalLineBreaks() {
        FuriganaDocument document = pipelineWithoutProvider()
                .process("1\n00:00:01,000 --> 00:00:02,000\n元気ですか？\n私は元気です。");

        assertEquals("元気ですか？\n私は元気です。", document.getEntries().get(0).text());
    }

    @Test
    @DisplayName("Removes passages between parentheses and discards entries that become empty")
    void stripsParenthesesWhenEnabled() {
        FuriganaPipeline pipeline = new FuriganaPipeline(new ScriptJapaneseTokenizer(), null, true);

        FuriganaDocument document = pipeline.process("""
                1
                00:00:01,000 --> 00:00:02,000
                （太郎）やあ（笑）

                2
                00:00:03,000 --> 00:00:04,000
                （笑）

                3
                00:00:05,000 --> 00:00:06,000
                元気ですか？
                """);

        assertEquals(2, document.size());
        FuriganaEntry first = document.getEntries().get(0);
        assertEquals("1", first.id());
        assertEquals("やあ", first.text());
        assertEquals("3", document.getEntries().get(1).id());
        assertEquals("元気ですか？", document.getEntries().get(1).text());
    }

    @Test
    @DisplayName("Keeps parentheses when cleaning is disabled")
    void keepsParenthesesWhenDisabled() {
        FuriganaDocument document = pipelineWithoutProvider()
                .process("1\n00:00:01,000 --> 00:00:02,000\n（太郎）やあ（笑）");

        assertEquals("（太郎）やあ（笑）", document.getEntries().get(0).text());
    }

    @Test
    @DisplayName("Tokenizes cleaned text without generating furigana for removed content")
    void tokenizesCleanedText() {
        List<FuriganaSegment> segments =
                new FuriganaPipeline(new ScriptJapaneseTokenizer(), null, true)
                        .process("1\n00:00:01,000 --> 00:00:02,000\n（太郎）日本語（にほんご）")
                        .getEntries().get(0).segments();

        assertEquals(List.of(new FuriganaSegment("日本語", null)), segments);
    }

    @Test
    @DisplayName("Returns an empty document for empty or null content")
    void returnsEmptyDocumentForNullOrBlank() {
        FuriganaPipeline pipeline = pipelineWithoutProvider();

        assertTrue(pipeline.process((String) null).getEntries().isEmpty());
        assertTrue(pipeline.process("").getEntries().isEmpty());
        assertTrue(pipeline.process("   \n  ").getEntries().isEmpty());
    }

    @Test
    @DisplayName("Integrates Kuromoji: splits kanji and okurigana with real readings")
    void integratesKuromojiAndSplitsOkurigana() {
        List<FuriganaSegment> segments = new FuriganaPipeline(new KuromojiTokenizer(), null)
                .process("1\n00:00:01,000 --> 00:00:02,000\n日本語を勉強します。")
                .getEntries().get(0).segments();

        assertTrue(segments.contains(new FuriganaSegment("日本語", "にほんご")), segments.toString());
        assertTrue(segments.contains(new FuriganaSegment("勉強", "べんきょう")), segments.toString());
        assertTrue(segments.contains(new FuriganaSegment("し", null)), segments.toString());
    }

    @Test
    @DisplayName("Splits the provider reading into kanji and okurigana")
    void splitsProviderReadingIntoKanjiAndOkurigana() {
        JapaneseTokenizer tokenizer = text -> List.of(new Token("食べる", "動詞"));
        ReadingProvider provider = token -> Optional.of(new Reading("食べる", "たべる", "test"));

        List<FuriganaSegment> segments = new FuriganaPipeline(tokenizer, provider)
                .process("1\n00:00:01,000 --> 00:00:02,000\n食べる")
                .getEntries().get(0).segments();

        assertEquals(List.of(new FuriganaSegment("食", "た"), new FuriganaSegment("べる", null)), segments);
    }

    @Test
    @DisplayName("Does not query the provider for tokens without kanji")
    void doesNotQueryProviderForKanaTokens() {
        AtomicInteger calls = new AtomicInteger();
        ReadingProvider provider = token -> {
            calls.incrementAndGet();
            return Optional.empty();
        };

        new FuriganaPipeline(new ScriptJapaneseTokenizer(), provider)
                .process("1\n00:00:01,000 --> 00:00:02,000\nありがとう。");

        assertEquals(0, calls.get());
    }

    @Test
    @DisplayName("Queries the provider only for tokens with kanji")
    void queriesProviderOnlyForKanjiTokens() {
        AtomicInteger calls = new AtomicInteger();
        ReadingProvider provider = token -> {
            calls.incrementAndGet();
            return Optional.empty();
        };

        new FuriganaPipeline(new ScriptJapaneseTokenizer(), provider)
                .process("1\n00:00:01,000 --> 00:00:02,000\n日本語は。");

        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("Integrates Kuromoji: aligns trailing okurigana without losing the reading (大嫌い)")
    void integratesKuromojiForTrailingOkurigana() {
        List<FuriganaSegment> segments = new FuriganaPipeline(new KuromojiTokenizer(), null)
                .process("1\n00:00:01,000 --> 00:00:02,000\n大嫌い")
                .getEntries().get(0).segments();

        assertEquals(List.of(
                new FuriganaSegment("大嫌", "だいきら"),
                new FuriganaSegment("い", null)), segments);
    }

    @Test
    @DisplayName("Warns when a term with kanji has no reading")
    void warnsWhenKanjiTokenHasNoReading() {
        List<String> warnings = new ArrayList<>();
        JapaneseTokenizer tokenizer = text -> List.of(new Token("大嫌い", "名詞"));
        ReadingProvider provider = token -> Optional.empty();

        new FuriganaPipeline(tokenizer, provider, false, warnings::add)
                .process("1\n00:00:01,000 --> 00:00:02,000\n大嫌い");

        assertEquals(List.of(
                "no reading for \"大嫌い\"; the term will stay without furigana."), warnings);
    }

    @Test
    @DisplayName("Warns only once per surface repeated in the same subtitle")
    void warnsOncePerRepeatedSurface() {
        List<String> warnings = new ArrayList<>();
        JapaneseTokenizer tokenizer = text -> List.of(new Token("大嫌い", "名詞"));
        ReadingProvider provider = token -> Optional.empty();

        new FuriganaPipeline(tokenizer, provider, false, warnings::add)
                .process("""
                        1
                        00:00:01,000 --> 00:00:02,000
                        大嫌い

                        2
                        00:00:03,000 --> 00:00:04,000
                        大嫌い
                        """);

        assertEquals(1, warnings.size(), warnings.toString());
    }

    @Test
    @DisplayName("Does not warn when the reading is found")
    void doesNotWarnWhenReadingFound() {
        List<String> warnings = new ArrayList<>();
        JapaneseTokenizer tokenizer = text -> List.of(new Token("大嫌い", "名詞"));
        ReadingProvider provider = token -> Optional.of(new Reading("大嫌い", "だいきらい", "test"));

        new FuriganaPipeline(tokenizer, provider, false, warnings::add)
                .process("1\n00:00:01,000 --> 00:00:02,000\n大嫌い");

        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    // ------------------------------------------------------------------
    // Utilities
    // ------------------------------------------------------------------

    private FuriganaPipeline pipelineWithoutProvider() {
        return new FuriganaPipeline(new ScriptJapaneseTokenizer(), null);
    }
}