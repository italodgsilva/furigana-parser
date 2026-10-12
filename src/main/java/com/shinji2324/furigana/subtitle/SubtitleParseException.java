package com.shinji2324.furigana.subtitle;

/**
 * Exception thrown when content in SRT format cannot be interpreted.
 *
 * <p>It is used to signal structural errors (missing or invalid timestamp),
 * allowing the caller to decide how to handle the problem instead of an entry
 * being silently discarded.
 */
public class SubtitleParseException extends RuntimeException {

    /**
     * Creates an exception with the given detail message.
     *
     * @param message error description
     */
    public SubtitleParseException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and root cause.
     *
     * @param message error description
     * @param cause   error cause
     */
    public SubtitleParseException(String message, Throwable cause) {
        super(message, cause);
    }
}