package com.shinji2324.furigana.furigana;

/**
 * Renderable unit of a furigana entry.
 *
 * <p>Represents the relationship between the original text and its reading,
 * without assuming how the result will be rendered (JSON, ASS, etc.). For
 * elements that have no furigana (hiragana, punctuation, spaces),
 * {@link #reading()} is {@code null} — see the conceptual model in
 * docs/architecture.md.
 *
 * @param text    original excerpt of the text
 * @param reading reading of the excerpt, or {@code null} when there is no furigana
 */
public record FuriganaSegment(String text, String reading) {
}