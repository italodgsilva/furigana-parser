package com.shinji2324.furigana.cli;

import java.nio.file.Path;

/**
 * Comando interpretado da linha de comando.
 *
 * <p>Para {@link Mode#GENERATE_JSON} e {@link Mode#GENERATE_ASS}, {@code output}
 * é o arquivo de saída. Para {@link Mode#PLAY}, {@code input} é a legenda SRT e
 * {@code output} é o arquivo de vídeo. Para {@link Mode#HELP}, ambos são
 * {@code null}.
 *
 * <p>{@code embedFont} liga a embedação da fonte no ASS. {@code fontPath}, quando
 * informado, aponta o arquivo de fonte a embutir; quando {@code null}, a fonte é
 * localizada pelo nome de família no sistema.
 *
 * @param mode      operação a executar
 * @param input     legenda SRT de entrada
 * @param output    destino (arquivo de saída ou vídeo), conforme o modo
 * @param embedFont se a fonte deve ser embutida no ASS
 * @param fontPath  arquivo de fonte a embutir, ou {@code null} para localizar
 */
public record CliCommand(Mode mode, Path input, Path output, boolean embedFont, Path fontPath) {

    /** Operação pedida na linha de comando. */
    public enum Mode {
        /** Exibir instruções de uso. */
        HELP,
        /** Gerar o modelo intermediário JSON. */
        GENERATE_JSON,
        /** Gerar a legenda ASS com furigana. */
        GENERATE_ASS,
        /** Gerar o ASS e abrir o vídeo no MPV. */
        PLAY
    }

    /** Cria o comando de ajuda. */
    public static CliCommand help() {
        return new CliCommand(Mode.HELP, null, null, false, null);
    }

    /**
     * Cria o comando de geração de JSON.
     *
     * @param input  legenda SRT
     * @param output arquivo JSON de saída
     * @return comando configurado
     */
    public static CliCommand generateJson(Path input, Path output) {
        return new CliCommand(Mode.GENERATE_JSON, input, output, false, null);
    }

    /**
     * Cria o comando de geração de ASS.
     *
     * @param input     legenda SRT
     * @param output    arquivo ASS de saída
     * @param embedFont se a fonte deve ser embutida no ASS
     * @param fontPath  arquivo de fonte a embutir, ou {@code null} para localizar
     * @return comando configurado
     */
    public static CliCommand generateAss(Path input, Path output, boolean embedFont, Path fontPath) {
        return new CliCommand(Mode.GENERATE_ASS, input, output, embedFont, fontPath);
    }

    /**
     * Cria o comando de reprodução no MPV.
     *
     * @param subtitle  legenda SRT
     * @param video     arquivo de vídeo
     * @param embedFont se a fonte deve ser embutida no ASS
     * @param fontPath  arquivo de fonte a embutir, ou {@code null} para localizar
     * @return comando configurado
     */
    public static CliCommand play(Path subtitle, Path video, boolean embedFont, Path fontPath) {
        return new CliCommand(Mode.PLAY, subtitle, video, embedFont, fontPath);
    }
}
