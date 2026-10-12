package com.shinji2324.furigana.output;

import com.shinji2324.furigana.furigana.FuriganaDocument;
import com.shinji2324.furigana.furigana.FuriganaEntry;
import com.shinji2324.furigana.furigana.FuriganaSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates the ASS subtitle with positioned furigana (strategy 3 of docs/ass-format.md).
 *
 * <p>Each visual line of the main text becomes an event in the {@code Main} style,
 * centered with {@code \pos}. Each reading becomes an additional event in the
 * {@code Furigana} style, positioned by {@code \pos} over the corresponding kanji,
 * using the character grid of {@link AssLayout}. The original text and timing are
 * preserved.
 *
 * <p>The generator receives an already processed {@link FuriganaDocument}: it does
 * not know SRT, Kuromoji, or Jisho. Styles and resolution live in the template
 * {@link AssTemplate} ({@code /ass/default-style.ass}).
 *
 * <p>The association between segments and visual lines is done by locating each
 * segment in the original text (by character offset). This works both with
 * tokenizers that preserve spaces and line breaks as tokens (Kuromoji) and with
 * those that discard them ({@code ScriptJapaneseTokenizer}).
 */
public class AssGenerator {

    /** Style of the main text. */
    static final String MAIN_STYLE = "Main";

    /** Furigana style. */
    static final String FURIGANA_STYLE = "Furigana";

    private final AssTemplate template;
    private final AssWriter writer;

    /** Creates the generator with the default template and the default writer. */
    public AssGenerator() {
        this(AssTemplate.defaultTemplate(), new AssWriter());
    }

    /**
     * Creates the generator with a given template.
     *
     * @param template ASS header (styles and resolution)
     */
    public AssGenerator(AssTemplate template) {
        this(template, new AssWriter());
    }

    /**
     * Creates the generator with a given template and writer.
     *
     * @param template ASS header
     * @param writer   event serializer
     */
    public AssGenerator(AssTemplate template, AssWriter writer) {
        this.template = template;
        this.writer = writer;
    }

    /**
     * Generates the complete ASS content from the processed document.
     *
     * @param document document with the furigana entries and segments
     * @return ASS content (header + events)
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
     * Converts an entry into ASS events (main text + furigana per line).
     */
    private List<AssEvent> eventsForEntry(FuriganaEntry entry) {
        String start = AssWriter.formatTime(entry.start());
        String end = AssWriter.formatTime(entry.end());

        String text = entry.text();
        String[] lines = text.split("\n", -1);

        // Absolute offset of the first character of each line.
        int[] lineStart = new int[lines.length];
        int runningOffset = 0;
        for (int i = 0; i < lines.length; i++) {
            lineStart[i] = runningOffset;
            runningOffset += lines[i].length() + 1; // +1 refers to the '\n' separator
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
     * Returns the index of the line that contains the given offset.
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
     * Builds the override prefix with center alignment and position.
     */
    private static String position(double x, double y) {
        return "{\\an5\\pos(" + Math.round(x) + "," + Math.round(y) + ")}";
    }
}
