package com.shinji2324.furigana.cli;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Interpreta os argumentos da linha de comando.
 *
 * <p>Formas aceitas:
 * <pre>
 * furigana-parser &lt;entrada.srt&gt; &lt;saida.json|saida.ass&gt;
 * furigana-parser --mpv &lt;entrada.srt&gt; &lt;video&gt;   (alias: play)
 * furigana-parser --help
 * </pre>
 *
 * <p>No modo de geração, o formato de saída é decidido pela extensão do arquivo
 * ({@code .json} ou {@code .ass}).
 */
public class CliParser {

    /** Texto de uso exibido em {@code --help} e em erros de argumentos. */
    public static final String USAGE = String.join("\n",
            "Uso:",
            "  furigana-parser <entrada.srt> <saida.json|saida.ass>",
            "  furigana-parser --mpv <entrada.srt> <video>",
            "  furigana-parser --help");

    private static final String PLAY_ALIAS = "play";

    /**
     * Converte os argumentos em um {@link CliCommand}.
     *
     * @param args argumentos da linha de comando
     * @return o comando interpretado
     * @throws CliUsageException se os argumentos não formarem um comando válido
     */
    public CliCommand parse(String[] args) {
        if (args == null || args.length == 0 || isHelp(args[0])) {
            return CliCommand.help();
        }

        if (isPlay(args[0])) {
            if (args.length != 3) {
                throw new CliUsageException("O modo --mpv exige a legenda SRT e o vídeo.\n" + USAGE);
            }
            return CliCommand.play(Path.of(args[1]), Path.of(args[2]));
        }

        if (args.length != 2) {
            throw new CliUsageException(USAGE);
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        return switch (extensionOf(output)) {
            case "json" -> CliCommand.generateJson(input, output);
            case "ass" -> CliCommand.generateAss(input, output);
            default -> throw new CliUsageException(
                    "Formato de saída não suportado: use .json ou .ass.\n" + USAGE);
        };
    }

    private static boolean isHelp(String argument) {
        return argument.equals("-h") || argument.equals("--help") || argument.equals("help");
    }

    private static boolean isPlay(String argument) {
        return argument.equals("--mpv") || argument.equals(PLAY_ALIAS);
    }

    /**
     * Retorna a extensão do caminho em minúsculas (sem o ponto), ou string vazia.
     */
    private static String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
