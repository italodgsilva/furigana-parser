package com.shinji2324.furigana.subtitle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do {@link SrtParser}.
 */
class SrtParserTest {

    private final SrtParser parser = new SrtParser();

    // ------------------------------------------------------------------
    // Conteúdo de exemplo (fixtures)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Parsing de legenda simples em ASCII")
    void parsesSimpleFixture() throws IOException {
        Subtitle subtitle = parser.parse(readFixture("simple.srt"));

        assertEquals(2, subtitle.size());
        List<SubtitleEntry> entries = subtitle.getEntries();

        SubtitleEntry first = entries.get(0);
        assertEquals("1", first.getId());
        assertEquals("00:00:01,000", first.getStart());
        assertEquals("00:00:02,500", first.getEnd());
        assertEquals("Hello, world!", first.getText());

        SubtitleEntry second = entries.get(1);
        assertEquals("2", second.getId());
        assertEquals("Second line, still ASCII.", second.getText());
    }

    @Test
    @DisplayName("Parsing de legenda japonesa com múltiplas linhas de texto")
    void parsesJapaneseFixture() throws IOException {
        Subtitle subtitle = parser.parse(readFixture("japanese.srt"));

        assertEquals(3, subtitle.size());
        List<SubtitleEntry> entries = subtitle.getEntries();

        assertEquals("今日は日本語を勉強します。", entries.get(0).getText());

        // Entrada com duas linhas de texto unidas por \n.
        assertEquals("元気ですか？\n私は元気です。", entries.get(1).getText());

        // Entrada que termina sem nova linha ao final do arquivo.
        assertEquals("これは長いセリフで、\n二行目に続きます。", entries.get(2).getText());
    }

    // ------------------------------------------------------------------
    // Variações aceitas do formato
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Aceita entrada sem índice numérico")
    void parsesEntryWithoutIndex() {
        String content = """
                00:00:01,000 --> 00:00:02,000
                Texto sem índice
                """;

        Subtitle subtitle = parser.parse(content);

        assertEquals(1, subtitle.size());
        SubtitleEntry entry = subtitle.getEntries().get(0);
        assertNull(entry.getId());
        assertEquals("Texto sem índice", entry.getText());
    }

    @Test
    @DisplayName("Reconhece separador de timestamp com espaços extras")
    void acceptsExtraSpacesAroundTimestampArrow() {
        String content = "1\n00:00:01,000   -->   00:00:02,000\nOlá!";

        Subtitle subtitle = parser.parse(content);

        assertEquals(1, subtitle.size());
        SubtitleEntry entry = subtitle.getEntries().get(0);
        assertEquals("00:00:01,000", entry.getStart());
        assertEquals("00:00:02,000", entry.getEnd());
    }

    @Test
    @DisplayName("Aceita timestamp com ponto em vez de vírgula")
    void acceptsTimestampWithDotSeparator() {
        String content = "1\n00:00:01.500 --> 00:00:02.750\nPonto decimal";

        Subtitle subtitle = parser.parse(content);

        assertEquals("00:00:01.500", subtitle.getEntries().get(0).getStart());
        assertEquals("00:00:02.750", subtitle.getEntries().get(0).getEnd());
    }

    @Test
    @DisplayName("Preserva a vírgula e a precisão do timestamp original")
    void preservesTimestampAsWritten() {
        String content = "1\n00:00:12,345 --> 00:01:13,999\nTeste";

        SubtitleEntry entry = parser.parse(content).getEntries().get(0);

        assertEquals("00:00:12,345", entry.getStart());
        assertEquals("00:01:13,999", entry.getEnd());
    }

    @Test
    @DisplayName("Une múltiplas linhas de texto usando \\n")
    void concatenatesMultilineTextWithNewline() {
        String content = "1\n00:00:01,000 --> 00:00:02,000\nPrimeira\nSegunda\nTerceira";

        SubtitleEntry entry = parser.parse(content).getEntries().get(0);

        assertEquals("Primeira\nSegunda\nTerceira", entry.getText());
    }

    @Test
    @DisplayName("Preserva espaços estruturais do texto da legenda")
    void preservesStructuralSpacesInText() {
        String content = "1\n00:00:01,000 --> 00:00:02,000\n  linha com recuo  ";

        SubtitleEntry entry = parser.parse(content).getEntries().get(0);

        assertEquals("  linha com recuo  ", entry.getText());
    }

    @Test
    @DisplayName("Ignora linhas em branco entre blocos")
    void ignoresBlankLinesBetweenBlocks() {
        String content = """
                1
                00:00:01,000 --> 00:00:02,000
                Primeiro

                   \s


                2
                00:00:03,000 --> 00:00:04,000
                Segundo
                """;

        Subtitle subtitle = parser.parse(content);

        assertEquals(2, subtitle.size());
        assertEquals("Segundo", subtitle.getEntries().get(1).getText());
    }

    @Test
    @DisplayName("Ignora uma única linha de ruído no fim do arquivo")
    void skipsSingleLineJunkAtEnd() {
        String content = """
                1
                00:00:01,000 --> 00:00:02,000
                Texto válido

                rodapé
                """;

        Subtitle subtitle = parser.parse(content);

        assertEquals(1, subtitle.size());
    }

    // ------------------------------------------------------------------
    // Fim de linha e codificação
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Aceita quebras de linha CRLF")
    void handlesCrlfLineEndings() {
        String content = "1\r\n00:00:01,000 --> 00:00:02,000\r\nTexto com CRLF\r\n\r\n"
                + "2\r\n00:00:03,000 --> 00:00:04,000\r\nSegunda\r\n";

        Subtitle subtitle = parser.parse(content);

        assertEquals(2, subtitle.size());
        assertEquals("Texto com CRLF", subtitle.getEntries().get(0).getText());
        assertEquals("Segunda", subtitle.getEntries().get(1).getText());
    }

    @Test
    @DisplayName("Aceita quebras de linha CR (Mac clássico)")
    void handlesCrLineEndings() {
        String content = "1\r00:00:01,000 --> 00:00:02,000\rTexto com CR\r\r2\r00:00:03,000 --> 00:00:04,000\rSegundo\r";

        Subtitle subtitle = parser.parse(content);

        assertEquals(2, subtitle.size());
        assertEquals("Texto com CR", subtitle.getEntries().get(0).getText());
    }

    @Test
    @DisplayName("Remove BOM UTF-8 no início do conteúdo")
    void handlesBomAtStartOfContent() {
        String content = "\uFEFF1\n00:00:01,000 --> 00:00:02,000\nCom BOM";

        Subtitle subtitle = parser.parse(content);

        assertEquals(1, subtitle.size());
        assertEquals("Com BOM", subtitle.getEntries().get(0).getText());
    }

    @Test
    @DisplayName("Aceita nova linha final após o último bloco")
    void handlesTrailingNewline() {
        String content = "1\n00:00:01,000 --> 00:00:02,000\nTexto\n";

        Subtitle subtitle = parser.parse(content);

        assertEquals(1, subtitle.size());
        assertEquals("Texto", subtitle.getEntries().get(0).getText());
    }

    // ------------------------------------------------------------------
    // Conteúdo vazio
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Retorna legenda vazia para conteúdo nulo")
    void returnsEmptySubtitleForNull() {
        assertTrue(parser.parse(null).getEntries().isEmpty());
    }

    @Test
    @DisplayName("Retorna legenda vazia para conteúdo em branco")
    void returnsEmptySubtitleForBlank() {
        assertTrue(parser.parse("   \n  ").getEntries().isEmpty());
        assertTrue(parser.parse("").getEntries().isEmpty());
    }

    // ------------------------------------------------------------------
    // Conteúdo inválido (fail-fast)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Lança exceção quando o bloco não possui timestamp")
    void throwsOnBlockWithoutTimestamp() {
        String content = "2\neste bloco não tem timestamp\ncontinuação";

        SubtitleParseException exception = assertThrows(SubtitleParseException.class, () -> parser.parse(content));
        assertNotNull(exception.getMessage());
    }

    // ------------------------------------------------------------------
    // Utilitários
    // ------------------------------------------------------------------

    /**
     * Lê uma fixture de legenda do classpath de teste (diretório {@code /subtitles}).
     */
    private String readFixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/subtitles/" + name)) {
            assertNotNull(in, "Fixture não encontrada no classpath: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}