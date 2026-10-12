package com.shinji2324.furigana.cli;

/**
 * Command-line usage error (missing or invalid arguments).
 *
 * <p>Signals that the application should print the usage instructions and exit
 * with the argument error code, without attempting to process anything.
 */
public class CliUsageException extends RuntimeException {

    /**
     * Creates the exception with the usage message.
     *
     * @param message message shown to the user
     */
    public CliUsageException(String message) {
        super(message);
    }
}
