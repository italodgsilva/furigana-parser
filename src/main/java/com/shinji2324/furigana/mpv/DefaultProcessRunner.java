package com.shinji2324.furigana.mpv;

import java.io.IOException;
import java.util.List;

/**
 * Default {@link ProcessRunner}, based on {@link ProcessBuilder}.
 *
 * <p>The process's input and output are inherited from the current process
 * ({@link ProcessBuilder#inheritIO()}), so that MPV's terminal interface works
 * normally.
 */
public class DefaultProcessRunner implements ProcessRunner {

    @Override
    public int run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).inheritIO().start();
        return process.waitFor();
    }
}
