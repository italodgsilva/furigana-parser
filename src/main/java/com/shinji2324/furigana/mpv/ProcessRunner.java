package com.shinji2324.furigana.mpv;

import java.io.IOException;
import java.util.List;

/**
 * Abstraction for running an external process.
 *
 * <p>It exists to keep the {@code mpv} layer testable: the tests inject an
 * implementation that merely records the command, without actually opening the
 * player.
 *
 * @see DefaultProcessRunner
 */
@FunctionalInterface
public interface ProcessRunner {

    /**
     * Runs the given command and waits for the process to finish.
     *
     * @param command command line already split into arguments
     * @return process exit code
     * @throws IOException          if the executable cannot be started (e.g. not in the PATH)
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    int run(List<String> command) throws IOException, InterruptedException;
}
