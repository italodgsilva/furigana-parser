package com.shinji2324.furigana.mpv;

import java.io.IOException;
import java.util.List;

/**
 * Abstração para execução de um processo externo.
 *
 * <p>Existe para manter a camada {@code mpv} testável: os testes injetam uma
 * implementação que apenas registra o comando, sem de fato abrir o player.
 *
 * @see DefaultProcessRunner
 */
@FunctionalInterface
public interface ProcessRunner {

    /**
     * Executa o comando informado e aguarda o término do processo.
     *
     * @param command linha de comando já dividida em argumentos
     * @return código de saída do processo
     * @throws IOException          se o executável não puder ser iniciado (ex.: não está no PATH)
     * @throws InterruptedException se a thread for interrompida enquanto aguarda
     */
    int run(List<String> command) throws IOException, InterruptedException;
}
