package com.shinji2324.furigana.font;

/**
 * A located font, ready to be embedded into an ASS.
 *
 * <p>{@code data} holds the bytes of a single face (a standalone {@code .ttf}
 * or {@code .otf} file). When the original font is a collection ({@code .ttc}),
 * the corresponding face is already extracted, since ASS embeds one face at a
 * time.
 *
 * @param fileName suggested name for the font, used in the {@code fontname:}
 *                 header of the {@code [Fonts]} section
 * @param data     binary content of the font face
 */
public record FontFile(String fileName, byte[] data) {
}
