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

        List<List<FuriganaItem>> furiganaItemsByLine = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            furiganaItemsByLine.add(new ArrayList<>());
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
            furiganaItemsByLine.get(lineIndex).add(new FuriganaItem(x, y, segment.reading()));
        }

        // Resolve horizontal collisions between furigana in the same line,
        // distributing the shift between adjacent items to keep them more centered.
        for (List<FuriganaItem> items : furiganaItemsByLine) {
            if (items.size() > 1) {
                resolveCollisions(items);
            }
        }

        List<List<AssEvent>> furiganaByLine = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            List<FuriganaItem> items = furiganaItemsByLine.get(i);
            List<AssEvent> eventsForLine = new ArrayList<>(items.size());
            for (FuriganaItem item : items) {
                eventsForLine.add(new AssEvent(
                        0, start, end, FURIGANA_STYLE, position(item.x, item.y) + item.reading));
            }
            furiganaByLine.add(eventsForLine);
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

    /** Minimum horizontal gap between adjacent furigana events, in pixels. */
    private static final double FURIGANA_COLLISION_GAP = 6.0;

    /** Maximum number of relaxation iterations when resolving collisions. */
    private static final int COLLISION_ITERATIONS = 5;

    /** Maximum horizontal distance a furigana can be shifted from its original position, in pixels. */
    private static final double MAX_SHIFT_FROM_ORIGINAL = 200.0;

    private static class FuriganaItem {
        double x;
        final double y;
        final double xOrig;
        final String reading;

        FuriganaItem(double x, double y, String reading) {
            this.x = x;
            this.y = y;
            this.xOrig = x;
            this.reading = reading;
        }

        double widthPx() {
            return AssLayout.cells(reading) * (double) AssLayout.FURIGANA_FONT_SIZE * AssLayout.EM_PER_FONT_SIZE;
        }

        double left() {
            return x - widthPx() / 2.0;
        }

        double right() {
            return x + widthPx() / 2.0;
        }
    }

    private static void resolveCollisions(List<FuriganaItem> items) {
        if (items.size() <= 1) {
            return;
        }
        items.sort((a, b) -> Double.compare(a.x, b.x));
        for (int iter = 0; iter < COLLISION_ITERATIONS; iter++) {
            boolean moved = false;
            // Left to right: distribute shift between adjacent items
            for (int i = 0; i < items.size() - 1; i++) {
                FuriganaItem left = items.get(i);
                FuriganaItem right = items.get(i + 1);
                if (right.left() < left.right() + FURIGANA_COLLISION_GAP) {
                    double overlap = (left.right() + FURIGANA_COLLISION_GAP) - right.left();
                    double half = overlap / 2.0;
                    // Shift left item slightly left, right item slightly right
                    left.x -= half;
                    right.x += half;
                    clamp(left);
                    clamp(right);
                    moved = true;
                }
            }
            // Right to left: balance
            for (int i = items.size() - 1; i > 0; i--) {
                FuriganaItem right = items.get(i);
                FuriganaItem left = items.get(i - 1);
                if (right.left() < left.right() + FURIGANA_COLLISION_GAP) {
                    double overlap = (left.right() + FURIGANA_COLLISION_GAP) - right.left();
                    double half = overlap / 2.0;
                    left.x -= half;
                    right.x += half;
                    clamp(left);
                    clamp(right);
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
        }
        // Re-sort after adjustments
        items.sort((a, b) -> Double.compare(a.x, b.x));
    }

    private static void clamp(FuriganaItem item) {
        double maxShift = MAX_SHIFT_FROM_ORIGINAL;
        if (item.x > item.xOrig + maxShift) {
            item.x = item.xOrig + maxShift;
        }
        if (item.x < item.xOrig - maxShift) {
            item.x = item.xOrig - maxShift;
        }
        double half = item.widthPx() / 2.0;
        if (item.x - half < 0) {
            item.x = half;
        }
        if (item.x + half > AssLayout.PLAY_RES_X) {
            item.x = AssLayout.PLAY_RES_X - half;
        }
    }

    /**
     * Builds the override prefix with center alignment and position.
     */
    private static String position(double x, double y) {
        return "{\\an5\\pos(" + Math.round(x) + "," + Math.round(y) + ")}";
    }
}
