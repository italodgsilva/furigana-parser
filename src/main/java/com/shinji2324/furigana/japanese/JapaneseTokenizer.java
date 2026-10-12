package com.shinji2324.furigana.japanese;

import java.util.List;

/**
 * Japanese text tokenizer.
 *
 * <p>Responsible for segmenting a string into {@link Token}s, preserving the
 * original order. The {@code japanese} layer knows nothing about readings,
 * furigana, or output formats.
 *
 * <p>This is an interface (the concrete implementation was an open decision in
 * architecture.md). The current implementation is {@link ScriptJapaneseTokenizer}
 * (segmentation by script type, provisional); the definitive implementation
 * will use Kuromoji in a later stage.
 */
public interface JapaneseTokenizer {

    /**
     * Segments the given text into tokens.
     *
     * @param text text to be tokenized
     * @return list of tokens in the order they appear in the text
     */
    List<Token> tokenize(String text);
}