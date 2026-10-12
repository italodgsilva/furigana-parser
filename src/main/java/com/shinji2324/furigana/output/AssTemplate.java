package com.shinji2324.furigana.output;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
