package com.shinji2324.furigana.pipeline;

import com.shinji2324.furigana.furigana.FuriganaDocument;
import com.shinji2324.furigana.furigana.FuriganaEntry;
import com.shinji2324.furigana.furigana.FuriganaSegment;
import com.shinji2324.furigana.japanese.ScriptJapaneseTokenizer;
import com.shinji2324.furigana.japanese.Token;
import com.shinji2324.furigana.reading.Reading;
import com.shinji2324.furigana.reading.ReadingProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do {@link FuriganaPipeline}.
 */
class FuriganaPipelineTest {

    @Test
    @DisplayName("Preserva id, timestamps e texto original de cada entrada")
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
    @DisplayName("Gera um segmento por token, com leitura nula quando não há provider")
    void createsOneSegmentPerTokenWithoutProvider() {
        FuriganaDocument document = pipelineWithoutProvider()
                .process("1\n00:00:01,000 --> 00:00:02,000\n今日は日本語。");

        FuriganaEntry entry = document.getEntries().get(0);

        assertEquals(List.of("今日", "は", "日本語", "。"),
                entry.segments().stream().map(FuriganaSegment::text).toList());
        assertTrue(entry.segments().stream().allMatch(segment -> segment.reading() == null));
    }

    @Test
    @DisplayName("Usa o provider de leituras quando configurado")
    void usesReadingProviderWhenConfigured() {
        ReadingProvider provider = token -> {
            if (token.getSurface().equals("日本語")) {
                return Optional.of(new Reading("日本語", "にほんご", "teste"));
            }
            return Optional.empty();
        };

        List<FuriganaSegment> segments = new FuriganaPipeline(new ScriptJapaneseTokenizer(), provider)
                .process("1\n00:00:01,000 --> 00:00:02,000\n日本語を勉強。")
                .getEntries().get(0).segments();

        assertEquals("にほんご", segments.get(0).reading());
        assertNull(segments.get(1).reading()); // を, sem leitura
        assertEquals("勉強", segments.get(2).text());
        assertNull(segments.get(2).reading());
    }

    @Test
    @DisplayName("Mantém entradas sem kanji (não descarta, evita dessincronização)")
    void keepsEntriesWithoutKanji() {
        FuriganaDocument document = pipelineWithoutProvider()
                .process("1\n00:00:01,000 --> 00:00:02,000\nありがとうございます。");

        assertEquals(1, document.size());
        assertEquals("ありがとうございます。", document.getEntries().get(0).text());
    }

    @Test
    @DisplayName("Preserva quebras de linha internas no texto da entrada")
    void preservesInternalLineBreaks() {
        FuriganaDocument document = pipelineWithoutProvider()
                .process("1\n00:00:01,000 --> 00:00:02,000\n元気ですか？\n私は元気です。");

        assertEquals("元気ですか？\n私は元気です。", document.getEntries().get(0).text());
    }

    @Test
    @DisplayName("Retorna documento vazio para conteúdo vazio ou nulo")
    void returnsEmptyDocumentForNullOrBlank() {
        FuriganaPipeline pipeline = pipelineWithoutProvider();

        assertTrue(pipeline.process((String) null).getEntries().isEmpty());
        assertTrue(pipeline.process("").getEntries().isEmpty());
        assertTrue(pipeline.process("   \n  ").getEntries().isEmpty());
    }

    // ------------------------------------------------------------------
    // Utilitários
    // ------------------------------------------------------------------

    private FuriganaPipeline pipelineWithoutProvider() {
        return new FuriganaPipeline(new ScriptJapaneseTokenizer(), null);
    }
}