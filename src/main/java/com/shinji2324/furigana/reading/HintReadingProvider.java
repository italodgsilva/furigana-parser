package com.shinji2324.furigana.reading;

import com.shinji2324.furigana.japanese.Token;

import java.util.Map;
import java.util.Optional;

public class HintReadingProvider implements ReadingProvider {

    private final Map<String, String> hints;

    public HintReadingProvider(Map<String, String> hints) {
        this.hints = hints == null ? Map.of() : hints;
    }

    @Override
    public Optional<Reading> findReading(Token token) {
        if (token == null || token.getSurface() == null) {
            return Optional.empty();
        }
        String reading = hints.get(token.getSurface());
        if (reading == null || reading.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Reading(token.getSurface(), reading, "hint"));
    }
}
