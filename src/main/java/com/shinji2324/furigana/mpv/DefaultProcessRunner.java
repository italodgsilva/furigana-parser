package com.shinji2324.furigana.mpv;

import java.io.IOException;
import java.util.List;

/**
 * {@link ProcessRunner} padrão, baseado em {@link ProcessBuilder}.
 *
 * <p>A entrada e a saída do processo são herdadas do processo atual
 * ({@link ProcessBuilder#inheritIO()}), de modo que a interface de terminal do
 * MPV funcione normalmente.
 */
public class DefaultProcessRunner implements ProcessRunner {

    @Override
    public int run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).inheritIO().start();
        return process.waitFor();
    }
}
