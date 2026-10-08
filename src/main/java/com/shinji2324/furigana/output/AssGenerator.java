package com.shinji2324.furigana.output;

import com.shinji2324.furigana.furigana.FuriganaDocument;
import com.shinji2324.furigana.furigana.FuriganaEntry;
import com.shinji2324.furigana.furigana.FuriganaSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * Gera a legenda ASS com furigana posicionado (estratégia 3 do docs/ass-format.md).
 *
 * <p>Cada linha visual do texto principal vira um evento no estilo {@code Main},
 * centralizado com {@code \pos}. Cada leitura vira um evento adicional no estilo
 * {@code Furigana}, posicionado por {@code \pos} sobre o kanji correspondente,
 * usando a grade de caracteres de {@link AssLayout}. O texto e a sincronização
 * originais são preservados.
 *
 * <p>O gerador recebe um {@link FuriganaDocument} já processado: ele não conhece
 * SRT, Kuromoji nem o Jisho. Estilos e resolução ficam no template
 * {@link AssTemplate} ({@code /ass/default-style.ass}).
 *
 * <p>A associação entre segmentos e linhas visuais é feita localizando cada
 * segmento no texto original (por deslocamento de caractere). Isso funciona tanto
 * com tokenizadores que preservam espaços e quebras de linha como tokens
 * (Kuromoji) quanto com os que os descartam ({@code ScriptJapaneseTokenizer}).
 */
public class AssGenerator {

    /** Estilo do texto principal. */
    static final String MAIN_STYLE = "Main";

    /** Estilo do furigana. */
    static final String FURIGANA_STYLE = "Furigana";

    private final AssTemplate template;
    private final AssWriter writer;

    /** Cria o gerador com o template padrão e o writer padrão. */
    public AssGenerator() {
        this(AssTemplate.defaultTemplate(), new AssWriter());
    }

    /**
     * Cria o gerador com um template informado.
     *
     * @param template cabeçalho ASS (estilos e resolução)
     */
    public AssGenerator(AssTemplate template) {
        this(template, new AssWriter());
    }

    /**
     * Cria o gerador com template e writer informados.
     *
     * @param template cabeçalho ASS
     * @param writer   serializador de eventos
     */
    public AssGenerator(AssTemplate template, AssWriter writer) {
        this.template = template;
        this.writer = writer;
    }

    /**
     * Gera o conteúdo ASS completo a partir do documento processado.
     *
     * @param document documento com as entradas e segmentos de furigana
     * @return conteúdo ASS (cabeçalho + eventos)
     */
    public String generate(FuriganaDocument document) {
        List<AssEvent> events = new ArrayList<>();
        if (document != null) {
            for (FuriganaEntry entry : document.getEntries()) {
                events.addAll(eventsForEntry(entry));
            }
        }
        return writer.write(template, events);
    }

    /**
     * Converte uma entrada em eventos ASS (texto principal + furigana por linha).
     */
    private List<AssEvent> eventsForEntry(FuriganaEntry entry) {
        String start = AssWriter.formatTime(entry.start());
        String end = AssWriter.formatTime(entry.end());

        String text = entry.text();
        String[] lines = text.split("\n", -1);

        // Deslocamento absoluto do primeiro caractere de cada linha.
        int[] lineStart = new int[lines.length];
        int runningOffset = 0;
        for (int i = 0; i < lines.length; i++) {
            lineStart[i] = runningOffset;
            runningOffset += lines[i].length() + 1; // +1 refere-se ao '\n' separador
        }

        List<List<AssEvent>> furiganaByLine = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            furiganaByLine.add(new ArrayList<>());
        }

        int cursor = 0;
        for (FuriganaSegment segment : entry.segments()) {
            String segmentText = segment.text();
            if (segmentText == null || segmentText.isEmpty()) {
                continue;
            }
            int index = text.indexOf(segmentText, cursor);
            if (index < 0) {
                continue;
            }
            cursor = index + segmentText.length();

            if (segment.reading() == null || segment.reading().isBlank()) {
                continue;
            }
            int lineIndex = lineIndexForOffset(index, lineStart);
            String line = lines[lineIndex];
            int column = index - lineStart[lineIndex];
            if (column < 0 || column + segmentText.length() > line.length()) {
                continue;
            }

            double totalCells = AssLayout.cells(line);
            double startCell = AssLayout.cells(line.substring(0, column));
            double centerCell = startCell + AssLayout.cells(segmentText) / 2.0;
            double x = AssLayout.xForCenterCell(centerCell, totalCells);
            double y = AssLayout.furiganaCenterY(lines.length - 1 - lineIndex);
            furiganaByLine.get(lineIndex).add(new AssEvent(
                    0, start, end, FURIGANA_STYLE, position(x, y) + segment.reading()));
        }

        List<AssEvent> events = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            if (line.isEmpty()) {
                continue;
            }
            int indexFromBottom = lines.length - 1 - lineIndex;
            double centerX = AssLayout.PLAY_RES_X / 2.0;
            double mainY = AssLayout.mainCenterY(indexFromBottom);
            events.add(new AssEvent(0, start, end, MAIN_STYLE, position(centerX, mainY) + line));
            events.addAll(furiganaByLine.get(lineIndex));
        }
        return events;
    }

    /**
     * Retorna o índice da linha que contém o deslocamento informado.
     */
    private static int lineIndexForOffset(int offset, int[] lineStart) {
        int result = 0;
        for (int i = 0; i < lineStart.length; i++) {
            if (lineStart[i] <= offset) {
                result = i;
            } else {
                break;
            }
        }
        return result;
    }

    /**
     * Monta o prefixo de override com alinhamento central e posição.
     */
    private static String position(double x, double y) {
        return "{\\an5\\pos(" + Math.round(x) + "," + Math.round(y) + ")}";
    }
}
