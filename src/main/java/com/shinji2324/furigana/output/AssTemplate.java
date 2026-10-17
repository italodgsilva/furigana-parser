package com.shinji2324.furigana.output;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Static header of an ASS file (Script Info, styles and event format).
 *
 * <p>Keeps the visual part separate from the generation logic, as described in
 * docs/ass-format.md: colors, fonts, sizes, and resolution can be changed in the
 * {@code /ass/default-style.ass} resource without touching {@link AssGenerator}.
 *
 * <p><strong>Warning:</strong> {@link AssLayout} assumes the resolution declared
 * here ({@code PlayResX}/{@code PlayResY} = 1920x1080). Changing the resolution in
 * the template requires adjusting the layout constants.
 */
public final class AssTemplate {

    /** Path of the default template on the classpath. */
    public static final String DEFAULT_RESOURCE = "/ass/default-style.ass";

    /**
     * Index of {@code PrimaryColour} inside a {@code Style:} line
     * ({@code "Style: Name"}, Fontname, Fontsize, PrimaryColour, …).
     */
    private static final int PRIMARY_COLOUR_INDEX = 3;

    private final String content;

    /**
     * Creates a template from already loaded content.
     *
     * @param content ASS header (up to the events {@code Format:} line)
     */
    public AssTemplate(String content) {
        this.content = content == null ? "" : content;
    }

    /**
     * Loads the default template from the classpath.
     *
     * @return the template embedded in the {@link #DEFAULT_RESOURCE} resource
     * @throws IllegalStateException if the resource cannot be read
     */
    public static AssTemplate defaultTemplate() {
        try (InputStream stream = AssTemplate.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("ASS resource not found: " + DEFAULT_RESOURCE);
            }
            return new AssTemplate(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read the ASS template: " + DEFAULT_RESOURCE, e);
        }
    }

    /**
     * Returns the template content.
     *
     * @return ASS header
     */
    public String content() {
        return content;
    }

    /**
     * Returns a template whose {@code PrimaryColour} of every style is set to the
     * given color.
     *
     * <p>The color may be a name ({@code red}, {@code yellow}, …), a {@code #RRGGBB}
     * / {@code RRGGBB} value or an ASS {@code AABBGGRR} value. An unrecognized color
     * (or a template without styles) returns the original template unchanged.
     *
     * @param template source template (may be {@code null})
     * @param color    color to apply (name, RGB or ASS color)
     * @return template with the new primary color
     */
    public static AssTemplate withColor(AssTemplate template, String color) {
        if (template == null || color == null || color.isBlank()) {
            return template;
        }
        String bgr = parseColor(color.trim());
        if (bgr == null) {
            return template;
        }
        String content = template.content();
        StringBuilder builder = new StringBuilder();
        boolean changed = false;
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("Style:")) {
                String[] fields = line.split(",", -1);
                if (fields.length > PRIMARY_COLOUR_INDEX) {
                    fields[PRIMARY_COLOUR_INDEX] = bgr;
                    line = String.join(",", fields);
                    changed = true;
                }
            }
            builder.append(line);
            if (i < lines.length - 1) {
                builder.append('\n');
            }
        }
        return changed ? new AssTemplate(builder.toString()) : template;
    }

    /**
     * Converts a color specification into an ASS color ({@code &HAABBGGRR}).
     *
     * @param color name, {@code #RRGGBB}/{@code RRGGBB} or {@code AABBGGRR} value
     * @return the ASS color, or {@code null} when the value is not recognized
     */
    private static String parseColor(String color) {
        String c = color.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (c.startsWith("#")) {
            c = c.substring(1);
        }
        // Named colors (common)
        switch (c) {
            case "yellow": return "&H0000FFFF";
            case "gold": return "&H0000D7FF";
            case "orange": return "&H00008CFF";
            case "red": return "&H000000FF";
            case "white": return "&H00FFFFFF";
            case "cyan": return "&H00FFFF00";
            case "magenta": return "&H00FF00FF";
            case "lime": return "&H0000FF00";
            case "blue": return "&H00FF0000";
            case "black": return "&H00000000";
        }
        // 6-digit RGB (RRGGBB) -> ASS AABBGGRR
        if (c.matches("[0-9a-f]{6}")) {
            String r = c.substring(0, 2);
            String g = c.substring(2, 4);
            String b = c.substring(4, 6);
            return ("&H00" + b + g + r).toUpperCase(Locale.ROOT);
        }
        // 8-digit ASS value (AABBGGRR), used as is
        if (c.matches("[0-9a-f]{8}")) {
            return ("&H" + c).toUpperCase(Locale.ROOT);
        }
        return null;
    }

    /**
     * Returns the distinct family names declared in the styles ({@code Fontname}),
     * in the order they appear.
     *
     * <p>Used by font embedding to know which font to locate and embed. A style is
     * a {@code Style: Name,Fontname,...} line — that is, the second field.
     *
     * @return distinct family names (empty list if the header has no styles)
     */
    public List<String> fontFamilies() {
        List<String> families = new ArrayList<>();
        for (String line : content.split("\n")) {
            String trimmed = line.strip();
            if (!trimmed.startsWith("Style:")) {
                continue;
            }
            String[] fields = trimmed.substring("Style:".length()).split(",", -1);
            if (fields.length < 2) {
                continue;
            }
            String family = fields[1].strip();
            if (!family.isEmpty() && !families.contains(family)) {
                families.add(family);
            }
        }
        return families;
    }
}
