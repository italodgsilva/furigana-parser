package com.shinji2324.furigana.reading.jisho;

import com.shinji2324.furigana.japanese.Token;
import com.shinji2324.furigana.reading.Reading;
import com.shinji2324.furigana.reading.ReadingProvider;

import java.util.Optional;

public class JishoReadingProvider implements ReadingProvider {

    @Override
    public Optional<Reading> findReading(Token token) {
        if (token == null || token.getSurface() == null || token.getSurface().isBlank()) {
            return Optional.empty();
        }

        // Implementação real será conectada ao Jisho em uma etapa posterior.
        return Optional.empty();
    }
}
