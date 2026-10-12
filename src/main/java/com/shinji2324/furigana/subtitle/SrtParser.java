package com.shinji2324.furigana.subtitle;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Subtitle parser in SRT (SubRip) format.
 *
 * <p>Format accepted per block:
 * <pre>
 * 1
 * 00:00:01,000 --> 00:00:02,500
 * Subtitle text
 * </pre>
 *
 * <p>Tolerance and handling rules:
 * <ul>
 *   <li>the numeric index is optional (some subtitles omit the numbering);</li>
 *   <li>the timestamp separator accepts extra spaces and comma or dot;</li>
 *   <li>blocks are separated by blank lines; CRLF, LF or CR line endings are accepted;</li>
 *   <li>text lines are joined with {@code \n} and preserve structural spaces
 *       (including leading and trailing spaces of each line);</li>
 *   <li>a UTF-8 BOM at the start of the content is removed.</li>
 * </ul>
 *
 * <p>Error policy: blocks with a single line are ignored (common noise at the end
 * of files), but larger blocks that do not form a valid entry throw
 * {@link SubtitleParseException}. The fail-fast approach prevents a corrupted
 * subtitle from silently desynchronizing the following entries.
 *
 * <p>This layer has no knowledge about Japanese, furigana or reading sources.
 */
public class SrtParser implements SubtitleParser {

    /**
     * SRT timestamp pattern: {@code H:MM:SS[,.]mmm}, with comma or dot and 1 to 3
     * fractional digits. The {@code -->} separator may have spaces around it.
     */
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            "(\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3})\\s*-->\\s*(\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3})");

    /** UTF-8 byte order mark (BOM), optionally present at the start of the file. */
    private static final char UTF_8_BOM = '\uFEFF';

    /** Fixed separator to join text lines, regardless of the operating system. */
    private static final String TEXT_LINE_SEPARATOR = "\n";

    @Override
    public Subtitle parse(String content) {
        Subtitle subtitle = new Subtitle();

        if (content == null || content.isBlank()) {
            return subtitle;
        }

        // Groups the lines into blocks separated by blank lines. This approach
        // is uniform for LF, CRLF and CR and does not depend on a separation
        // pattern that could break the text line into improper blocks.
        List<String> currentBlock = new ArrayList<>();
        for (String line : stripBom(content).split("\\R", -1)) {
            if (line.isBlank()) {
                finishBlock(currentBlock, subtitle);
            } else {
                currentBlock.add(line);
            }
        }
        finishBlock(currentBlock, subtitle);
        return subtitle;
    }

    /**
     * Processes the accumulated block (if any) and clears the list for the next one.
     *
     * @param block    lines of the block in progress
     * @param subtitle result being built
     */
    private void finishBlock(List<String> block, Subtitle subtitle) {
        if (!block.isEmpty()) {
            parseBlock(block, subtitle);
            block.clear();
        }
    }

    /**
     * Analyzes a subtitle block and adds the corresponding entry to the result.
     *
     * <p>The block already arrives without blank lines. Unlike a {@code trim} on the
     * raw text, the lines are not modified, preserving structural spaces.
     *
     * @param lines    lines of the block
     * @param subtitle result being built
     * @throws SubtitleParseException if the block cannot be interpreted as an entry
     */
    private void parseBlock(List<String> lines, Subtitle subtitle) {
        String firstLine = lines.get(0);

        // Format without index: the first line is already the timestamp.
        if (isTimestampLine(firstLine)) {
            addEntry(subtitle, null, firstLine, lines, 1);
            return;
        }

        // A single line without a timestamp does not contain enough temporal
        // information to form an entry; it is treated as noise (e.g. markers at the
        // end of the file).
        if (lines.size() == 1) {
            return;
        }

        // Standard format: index + timestamp + text.
        String timestampLine = lines.get(1);
        if (!isTimestampLine(timestampLine)) {
            throw new SubtitleParseException("Missing or invalid timestamp in subtitle block:\n"
                    + String.join(TEXT_LINE_SEPARATOR, lines));
        }
        addEntry(subtitle, lines.get(0).trim(), timestampLine, lines, 2);
    }

    /**
     * Checks whether a line matches an SRT timestamp (start and end separated by {@code -->}).
     */
    private boolean isTimestampLine(String line) {
        return TIMESTAMP_PATTERN.matcher(line).matches();
    }

    /**
     * Creates a {@link SubtitleEntry} from the lines of a block and adds it to the result.
     *
     * @param subtitle      result being built
     * @param id            entry index, or {@code null} when the subtitle has no numbering
     * @param timestampLine line containing the time range
     * @param lines         all lines of the block
     * @param textStart     index of the first text line within {@code lines}
     */
    private void addEntry(Subtitle subtitle, String id, String timestampLine,
                          List<String> lines, int textStart) {
        String[] timestamps = extractTimestamps(timestampLine);
        SubtitleEntry entry = new SubtitleEntry(
                id, timestamps[0], timestamps[1], joinTextLines(lines, textStart));
        subtitle.addEntry(entry);
    }

    /**
     * Extracts the start and end timestamps from a line in the {@code start --> end} format.
     *
     * @throws SubtitleParseException if the line does not contain a valid timestamp
     */
    private String[] extractTimestamps(String timestampLine) {
        Matcher matcher = TIMESTAMP_PATTERN.matcher(timestampLine);
        if (!matcher.matches()) {
            throw new SubtitleParseException("Invalid timestamp line: " + timestampLine);
        }
        return new String[]{matcher.group(1), matcher.group(2)};
    }

    /**
     * Joins the text lines of an entry with {@code \n}.
     *
     * <p>The lines are not cleaned at the edges: structural spaces present in the
     * original text are preserved.
     */
    private String joinTextLines(List<String> lines, int textStart) {
        return String.join(TEXT_LINE_SEPARATOR, lines.subList(textStart, lines.size()));
    }

    /**
     * Removes a UTF-8 BOM from the start of the content, when present.
     */
    private String stripBom(String content) {
        if (!content.isEmpty() && content.charAt(0) == UTF_8_BOM) {
            return content.substring(1);
        }
        return content;
    }
}