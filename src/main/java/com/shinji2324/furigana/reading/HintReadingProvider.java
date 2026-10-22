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
        String surface = token.getSurface();
        String reading = hints.get(surface);
        if (reading != null && !reading.isBlank()) {
            return Optional.of(new Reading(surface, reading, "hint"));
        }
        if (surface.isEmpty()) {
            return Optional.empty();
        }
        // Try to match if this token is part of a hinted name
        for (Map.Entry<String, String> entry : hints.entrySet()) {
            String key = entry.getKey();
            String fullReading = entry.getValue();
            if (key == null || key.isBlank() || fullReading == null || fullReading.isBlank()) {
                continue;
            }
            if (key.equals(surface)) {
                continue;
            }
            if (!key.contains(surface)) {
                continue;
            }
            // Find all occurrences
            int start = 0;
            while (true) {
                int idx = key.indexOf(surface, start);
                if (idx < 0) {
                    break;
                }
                if (idx + surface.length() == key.length()) {
                    // at end
                    int tokenCharCount = surface.codePointCount(0, surface.length());
                    if (tokenCharCount == 1 && fullReading.length() >= 1) {
                        // try last char
                        String tokenReading = fullReading.substring(fullReading.length() - 1);
                        if (isValidReading(tokenReading)) {
                            return Optional.of(new Reading(surface, tokenReading, "hint"));
                        }
                    }
                } else if (idx == 0) {
                    int tokenCharCount = surface.codePointCount(0, surface.length());
                    if (tokenCharCount > 0 && tokenCharCount <= fullReading.length()) {
                        String tokenReading = fullReading.substring(0, tokenCharCount);
                        if (isValidReading(tokenReading)) {
                            return Optional.of(new Reading(surface, tokenReading, "hint"));
                        }
                    }
                } else {
                    // middle - try to map by character position
                    int charCountBefore = key.substring(0, idx).length();
                    int tokenCharCount = surface.length();
                    int readingStart = charCountBefore;
                    if (readingStart >= 0 && readingStart < fullReading.length()) {
                        // try to take tokenCharCount chars
                        if (readingStart + tokenCharCount <= fullReading.length()) {
                            String tokenReading = fullReading.substring(readingStart, readingStart + tokenCharCount);
                            if (isValidReading(tokenReading)) {
                                return Optional.of(new Reading(surface, tokenReading, "hint"));
                            }
                        } else if (readingStart < fullReading.length()) {
                            // take rest
                            String tokenReading = fullReading.substring(readingStart);
                            if (isValidReading(tokenReading) && tokenReading.length() <= tokenCharCount * 2) {
                                return Optional.of(new Reading(surface, tokenReading, "hint"));
                            }
                        }
                    }
                }
                start = idx + 1;
            }
        }
        return Optional.empty();
    }

    private boolean isValidReading(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x3041 && c <= 0x3096) {
                continue;
            }
            if (c >= 0x30A1 && c <= 0x30FF) {
                continue;
            }
            if (c == 0x30FB) {
                continue;
            }
            return false;
        }
        return true;
    }
}
