package com.shinji2324.furigana.reading;

import com.shinji2324.furigana.japanese.Token;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HintReadingProviderTest {

    @Test
    void returnsReadingForHintedToken() {
        HintReadingProvider provider = new HintReadingProvider(Map.of("五条悟", "ごじょうさとる"));
        Token token = new Token("五条悟", "名詞", null, 0, 3);
        Optional<Reading> reading = provider.findReading(token);
        assertTrue(reading.isPresent());
        assertEquals("ごじょうさとる", reading.get().getReading());
        assertEquals("hint", reading.get().getSource());
    }

    @Test
    void returnsEmptyForUnknownToken() {
        HintReadingProvider provider = new HintReadingProvider(Map.of("五条悟", "ごじょうさとる"));
        Token token = new Token("虎杖", "名詞", null, 0, 2);
        assertTrue(provider.findReading(token).isEmpty());
    }
}
