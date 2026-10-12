package com.shinji2324.furigana.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Interpreta os argumentos da linha de comando.
 *
 * <p>Formas aceitas:
 * <pre>
 * furigana-parser [--strip-parentheses] &lt;entrada.srt&gt; &lt;saida.json|saida.ass&gt;
 * furigana-parser [--strip-parentheses] --mpv &lt;entrada.srt&gt; &lt;video&gt;   (alias: play)
 * furigana-parser [--strip-parentheses] [--embed-font[=&lt;arquivo&gt;]] [--subset-font] &lt;entrada.srt&gt; &lt;saida.ass&gt;
 * furigana-parser --help
 * </pre>
 *
 * <p>No modo de geração, o formato de saída é decidido pela extensão do arquivo
 * ({@code .json} ou {@code .ass}). A opção {@code --embed-font} embute a fonte da
 * legenda no próprio ASS e {@code --subset-font} (que exige a anterior) reduz a
 * fonte embutida aos glifos usados (veja {@code docs/font-embedding.md}). A opção
 * {@code --strip-parentheses} remove os trechos entre parênteses redondos do texto
 * antes do furigana (vale para JSON, ASS e {@code --mpv}).
 */
public class CliParser {

    /** Opção que liga a embedação da fonte no ASS. */
    private static final String EMBED_FONT = "--embed-font";

    /** Opção que liga a redução da fonte embutida aos glifos usados. */
    private static final String SUBSET_FONT = "--subset-font";

    /** Opção que remove os trechos entre parênteses redondos do texto. */
    private static final String STRIP_PARENTHESES = "--strip-parentheses";

    /** Texto de uso exibido em {@code --help} e em erros de argumentos. */
    public static final String USAGE = String.join("\n",
            "Uso:",
            "  furigana-parser [--strip-parentheses] <entrada.srt> <saida.json|saida.ass>",
            "  furigana-parser [--strip-parentheses] --mpv <entrada.srt> <video>",
            "  furigana-parser [--strip-parentheses] [--embed-font[=<arquivo>]] [--subset-font] "
                    + "<entrada.srt> <saida.ass>",
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

        boolean embedFont = false;
        boolean subsetFont = false;
        boolean stripParentheses = false;
        Path fontPath = null;
        List<String> positional = new ArrayList<>();
        for (String argument : args) {
            if (argument.equals(EMBED_FONT)) {
                embedFont = true;
            } else if (argument.equals(SUBSET_FONT)) {
                subsetFont = true;
            } else if (argument.equals(STRIP_PARENTHESES)) {
                stripParentheses = true;
            } else if (argument.startsWith(EMBED_FONT + "=")) {
                embedFont = true;
                String value = argument.substring(EMBED_FONT.length() + 1);
                if (value.isBlank()) {
                    throw new CliUsageException(
                            "Informe o arquivo de fonte em " + EMBED_FONT + "=<arquivo>.\n" + USAGE);
                }
                fontPath = Path.of(value);
            } else {
                positional.add(argument);
            }
        }

        if (positional.isEmpty() || isHelp(positional.get(0))) {
            return CliCommand.help();
        }

        if (subsetFont && !embedFont) {
            throw new CliUsageException(
                    "A opção " + SUBSET_FONT + " exige " + EMBED_FONT + ".\n" + USAGE);
        }

        if (isPlay(positional.get(0))) {
            if (positional.size() != 3) {
                throw new CliUsageException("O modo --mpv exige a legenda SRT e o vídeo.\n" + USAGE);
            }
            return CliCommand.play(Path.of(positional.get(1)), Path.of(positional.get(2)),
                    embedFont, subsetFont, fontPath, stripParentheses);
        }

        if (positional.size() != 2) {
            throw new CliUsageException(USAGE);
        }

        Path input = Path.of(positional.get(0));
        Path output = Path.of(positional.get(1));
        return switch (extensionOf(output)) {
            case "json" -> CliCommand.generateJson(input, output, stripParentheses);
            case "ass" -> CliCommand.generateAss(input, output, embedFont, subsetFont, fontPath,
                    stripParentheses);
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
