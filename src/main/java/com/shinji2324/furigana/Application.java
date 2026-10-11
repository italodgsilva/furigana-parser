package com.shinji2324.furigana;

import com.shinji2324.furigana.cli.CliCommand;
import com.shinji2324.furigana.cli.CliParser;
import com.shinji2324.furigana.cli.CliUsageException;
import com.shinji2324.furigana.font.FontFile;
import com.shinji2324.furigana.font.FontLocator;
import com.shinji2324.furigana.furigana.FuriganaDocument;
import com.shinji2324.furigana.japanese.JapaneseTokenizer;
import com.shinji2324.furigana.japanese.KuromojiTokenizer;
import com.shinji2324.furigana.mpv.MpvIntegration;
import com.shinji2324.furigana.mpv.MpvPaths;
import com.shinji2324.furigana.output.AssFontEmbedder;
import com.shinji2324.furigana.output.AssGenerator;
import com.shinji2324.furigana.output.AssTemplate;
import com.shinji2324.furigana.output.JsonWriter;
import com.shinji2324.furigana.pipeline.FuriganaPipeline;
import com.shinji2324.furigana.reading.CachingReadingProvider;
import com.shinji2324.furigana.reading.FileReadingCache;
import com.shinji2324.furigana.reading.ReadingProvider;
import com.shinji2324.furigana.reading.jisho.JishoReadingProvider;
import com.shinji2324.furigana.subtitle.SubtitleParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ponto de entrada da aplicação (CLI).
 *
 * <p>Formas de uso:
 * <pre>
 * furigana-parser &lt;entrada.srt&gt; &lt;saida.json|saida.ass&gt;
 * furigana-parser --mpv &lt;entrada.srt&gt; &lt;video&gt;
 * furigana-parser --help
 * </pre>
 *
 * <p>O formato de saída é escolhido pela extensão do arquivo de saída:
 * {@code .json} gera o modelo intermediário e {@code .ass} gera a legenda com o
 * furigana posicionado.
 *
 * <p>No modo {@code --mpv}, a legenda é gerada ao lado da SRT (sufixo
 * {@code .furigana.ass}) e o vídeo é aberto no MPV com
 * {@code --sub-file}. Veja as limitações em {@code docs/mpv.md}.
 *
 * <p>Lê a legenda SRT em UTF-8 e executa o pipeline. As leituras vêm do
 * tokenizador morfológico ({@link KuromojiTokenizer}); quando ele não conhece um
 * termo com kanji, o fallback consulta o {@link JishoReadingProvider}, sempre
 * mediado pelo {@link CachingReadingProvider} (cache em
 * {@code data/cache/readings.json}).
 *
 * <p>Esta classe é apenas o ponto de composição: a lógica permanece nas camadas
 * do pipeline. Ela não contém regras de negócio.
 */
public class Application {

    /** Código de saída para sucesso. */
    static final int EXIT_OK = 0;

    /** Código de saída para erro de processamento. */
    static final int EXIT_ERROR = 1;

    /** Código de saída para erro de argumentos. */
    static final int EXIT_USAGE = 2;

    /** Local padrão do cache de leituras, relativo ao diretório de execução. */
    private static final Path DEFAULT_CACHE_FILE = Path.of("data", "cache", "readings.json");

    private final CliParser cliParser = new CliParser();
    private final MpvIntegration mpvIntegration = new MpvIntegration();

    /**
     * Ponto de entrada da CLI.
     *
     * @param args argumentos da linha de comando
     */
    public static void main(String[] args) {
        int exitCode = new Application().run(args);
        if (exitCode != EXIT_OK) {
            System.exit(exitCode);
        }
    }

    /**
     * Executa a aplicação e retorna o código de saída.
     *
     * <p>Separado de {@link #main(String[])} para não encerrar a JVM ao ser usado
     * em testes.
     *
     * @param args argumentos da linha de comando
     * @return código de saída
     */
    public int run(String[] args) {
        CliCommand command;
        try {
            command = cliParser.parse(args);
        } catch (CliUsageException e) {
            System.err.println(e.getMessage());
            return EXIT_USAGE;
        }

        try {
            return execute(command);
        } catch (SubtitleParseException e) {
            System.err.println("Legenda inválida: " + e.getMessage());
            return EXIT_ERROR;
        } catch (IllegalArgumentException e) {
            System.err.println("Saída inválida: " + e.getMessage());
            return EXIT_ERROR;
        } catch (IOException e) {
            // JsonProcessingException é subclasse de IOException, portanto é coberta aqui.
            System.err.println("Erro ao processar legenda: " + e.getMessage());
            return EXIT_ERROR;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Execução interrompida: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    /**
     * Despacha o comando interpretado.
     */
    private int execute(CliCommand command) throws IOException, InterruptedException {
        return switch (command.mode()) {
            case HELP -> {
                System.out.println(CliParser.USAGE);
                yield EXIT_OK;
            }
            case GENERATE_JSON -> generate(command, false);
            case GENERATE_ASS -> generate(command, true);
            case PLAY -> play(command);
        };
    }

    /**
     * Gera a saída solicitada (JSON ou ASS) para o arquivo informado.
     */
    private int generate(CliCommand command, boolean ass) throws IOException {
        FuriganaDocument document = process(command.input());
        String rendered;
        if (ass) {
            rendered = renderAss(document, command);
        } else {
            rendered = new JsonWriter().write(document);
        }
        writeText(command.output(), rendered);
        System.out.println("Furigana gerado (" + (ass ? "ass" : "json") + "): "
                + command.output().toAbsolutePath());
        return EXIT_OK;
    }

    /**
     * Gera o ASS ao lado da SRT e abre o vídeo no MPV.
     */
    private int play(CliCommand command) throws IOException, InterruptedException {
        Path subtitle = command.input();
        Path video = command.output();
        Path ass = MpvPaths.furiganaSubtitle(subtitle);
        FuriganaDocument document = process(subtitle);
        writeText(ass, renderAss(document, command));
        System.out.println("Legenda gerada: " + ass.toAbsolutePath());
        System.out.println("Abrindo MPV: " + video.toAbsolutePath());

        try {
            int exitCode = mpvIntegration.open(video, ass);
            if (exitCode != 0) {
                System.err.println("MPV encerrou com código " + exitCode);
                return EXIT_ERROR;
            }
            return EXIT_OK;
        } catch (IOException e) {
            System.err.println("Falha ao iniciar o MPV (verifique se 'mpv' está instalado e no PATH): "
                    + e.getMessage());
            return EXIT_ERROR;
        }
    }

    /**
     * Monta o ASS com o furigana posicionado e, quando pedido, com a fonte
     * embutida.
     */
    private String renderAss(FuriganaDocument document, CliCommand command) throws IOException {
        String ass = new AssGenerator().generate(document);
        if (!command.embedFont()) {
            return ass;
        }
        return new AssFontEmbedder().embed(ass, resolveFonts(command.fontPath()));
    }

    /**
     * Resolve os arquivos das fontes declaradas no template ASS.
     *
     * <p>Com {@code fontPath} informado, embute esse arquivo; caso contrário,
     * localiza cada família no sistema.
     */
    private List<FontFile> resolveFonts(Path fontPath) throws IOException {
        List<String> families = AssTemplate.defaultTemplate().fontFamilies();
        if (families.isEmpty()) {
            throw new IOException("O template ASS não declara estilos com fonte para embutir.");
        }
        if (fontPath != null) {
            FontLocator locator = new FontLocator();
            return List.of(locator.read(fontPath, families.get(0)));
        }

        FontLocator locator = new FontLocator();
        List<FontFile> fonts = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String family : families) {
            Optional<FontFile> font = locator.find(family);
            if (font.isPresent()) {
                fonts.add(font.get());
            } else {
                missing.add(family);
            }
        }
        if (!missing.isEmpty()) {
            throw new IOException("Fonte não encontrada no sistema: " + String.join(", ", missing)
                    + ". Informe o arquivo com --embed-font=<arquivo>.");
        }
        return fonts;
    }

    /**
     * Lê a SRT e executa o pipeline completo de furigana.
     */
    private FuriganaDocument process(Path input) throws IOException {
        String srtContent = Files.readString(input, StandardCharsets.UTF_8);

        JapaneseTokenizer tokenizer = new KuromojiTokenizer();
        ReadingProvider provider = new CachingReadingProvider(
                new JishoReadingProvider(),
                new FileReadingCache(DEFAULT_CACHE_FILE));
        FuriganaPipeline pipeline = new FuriganaPipeline(tokenizer, provider);

        return pipeline.process(srtContent);
    }

    /**
     * Grava o conteúdo em UTF-8, criando o diretório pai quando necessário.
     */
    private static void writeText(Path output, String content) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, content, StandardCharsets.UTF_8);
    }
}
