package com.shinji2324.furigana.output;

import com.shinji2324.furigana.furigana.FuriganaDocument;
import com.shinji2324.furigana.furigana.FuriganaEntry;
import com.shinji2324.furigana.furigana.FuriganaSegment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do {@link AssGenerator}: cabeçalho, eventos do texto principal e
 * posicionamento do furigana.
 */
class AssGeneratorTest {

    private final AssGenerator generator = new AssGenerator();

    @Test
    @DisplayName("Carrega o template padrão com os estilos Main e Furigana")
    void loadsDefaultTemplate() {
        String content = AssTemplate.defaultTemplate().content();

        assertTrue(content.contains("[Script Info]"));
        assertTrue(content.contains("PlayResX: 1920"));
        assertTrue(content.contains("Style: Main,"));
        assertTrue(content.contains("Style: Furigana,"));
        assertTrue(content.contains("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"));
    }

    @Test
    @DisplayName("Gera o texto principal e o furigana posicionado de uma linha")
    void generatesSingleLineWithPositionedFurigana() {
        FuriganaDocument document = new FuriganaDocument(List.of(new FuriganaEntry(
                "1", "00:00:01,000", "00:00:04,500",
                "今日は日本語を勉強します。",
                List.of(
                        new FuriganaSegment("今日", "きょう"),
                        new FuriganaSegment("は", null),
                        new FuriganaSegment("日本語", "にほんご"),
                        new FuriganaSegment("を", null),
                        new FuriganaSegment("勉強", "べんきょう"),
                        new FuriganaSegment("し", null),
                        new FuriganaSegment("ます", null),
                        new FuriganaSegment("。", null)))));

        String output = generator.generate(document);

        // Texto principal: uma única linha, centralizado na tela, na base.
        assertTrue(output.contains(
                "Dialogue: 0,0:00:01.00,0:00:04.50,Main,,0,0,0,,{\\an5\\pos(960,988)}今日は日本語を勉強します。"));

        // Furigana alinhado sobre cada bloco de kanji (13 células, início em 544px).
        assertTrue(output.contains("Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\\an5\\pos(608,928)}きょう"));
        assertTrue(output.contains("Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\\an5\\pos(832,928)}にほんご"));
        assertTrue(output.contains("Dialogue: 0,0:00:01.00,0:00:04.50,Furigana,,0,0,0,,{\\an5\\pos(1056,928)}べんきょう"));

        // Apenas os três blocos com kanji geram furigana.
        assertEquals(3, countOccurrences(output, ",Furigana,"));
        assertEquals(1, countOccurrences(output, ",Main,"));
    }

    @Test
    @DisplayName("Trata entradas de duas linhas, associando o furigana à linha correta")
    void handlesTwoLineEntry() {
        FuriganaDocument document = new FuriganaDocument(List.of(new FuriganaEntry(
                "2", "00:00:05,000", "00:00:07,000",
                "元気ですか？\n元気です。",
                List.of(
                        new FuriganaSegment("元気", "げんき"),
                        new FuriganaSegment("ですか？", null),
                        new FuriganaSegment("元気", "げんき"),
                        new FuriganaSegment("です。", null)))));

        String output = generator.generate(document);

        // Duas linhas principais: a de cima em 868 e a de baixo em 988.
        assertTrue(output.contains("{\\an5\\pos(960,868)}元気ですか？"));
        assertTrue(output.contains("{\\an5\\pos(960,988)}元気です。"));
        // O furigana acompanha a linha: y=808 (topo) e y=928 (base).
        // Linha 1 (6 células, início em 768px): centro de "元気" em 832.
        // Linha 2 (5 células, início em 800px): centro de "元気" em 864.
        assertTrue(output.contains(",Furigana,,0,0,0,,{\\an5\\pos(832,808)}げんき"));
        assertTrue(output.contains(",Furigana,,0,0,0,,{\\an5\\pos(864,928)}げんき"));
        assertEquals(2, countOccurrences(output, ",Main,"));
        assertEquals(2, countOccurrences(output, ",Furigana,"));
    }

    @Test
    @DisplayName("Ignora o segmento de quebra de linha ao associar furigana (Kuromoji)")
    void handlesNewlineSegment() {
        // O Kuromoji emite a quebra de linha como um segmento sem leitura;
        // ele não deve interromper a associação dos segmentos da linha seguinte.
        FuriganaDocument document = new FuriganaDocument(List.of(new FuriganaEntry(
                "2", "00:00:05,000", "00:00:07,000",
                "元気ですか？\n私は元気です。",
                List.of(
                        new FuriganaSegment("元気", "げんき"),
                        new FuriganaSegment("です", null),
                        new FuriganaSegment("か", null),
                        new FuriganaSegment("？", null),
                        new FuriganaSegment("\n", null),
                        new FuriganaSegment("私", "わたし"),
                        new FuriganaSegment("は", null),
                        new FuriganaSegment("元気", "げんき"),
                        new FuriganaSegment("です", null),
                        new FuriganaSegment("。", null)))));

        String output = generator.generate(document);

        // "私" e "元気" da segunda linha precisam gerar furigana (além de "元気" da primeira).
        assertTrue(output.contains(",Furigana,,0,0,0,,{\\an5\\pos("));
        assertTrue(output.contains("}わたし"));
        assertTrue(output.contains("}げんき"));
        assertEquals(2, countOccurrences(output, ",Main,"));
        assertEquals(3, countOccurrences(output, ",Furigana,"));
    }

    @Test
    @DisplayName("Não gera furigana para texto apenas em kana")
    void doesNotGenerateFuriganaForKanaOnly() {
        FuriganaDocument document = new FuriganaDocument(List.of(new FuriganaEntry(
                "3", "00:00:08,000", "00:00:09,000",
                "ありがとう。",
                List.of(
                        new FuriganaSegment("ありがとう", null),
                        new FuriganaSegment("。", null)))));

        String output = generator.generate(document);

        assertEquals(1, countOccurrences(output, ",Main,"));
        assertEquals(0, countOccurrences(output, ",Furigana,"));
    }

    @Test
    @DisplayName("Gera documento vazio apenas com o cabeçalho")
    void generatesOnlyHeaderForEmptyDocument() {
        String output = generator.generate(new FuriganaDocument());

        assertTrue(output.contains("[Script Info]"));
        assertFalse(output.contains("Dialogue:"));
    }

    @Test
    @DisplayName("Aceita documento nulo sem quebrar")
    void toleratesNullDocument() {
        String output = generator.generate(null);

        assertTrue(output.contains("[Script Info]"));
        assertFalse(output.contains("Dialogue:"));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
