package com.shinji2324.furigana.reading;

import com.shinji2324.furigana.japanese.Token;

import java.util.Optional;

public interface ReadingProvider {
    Optional<Reading> findReading(Token token);
}
