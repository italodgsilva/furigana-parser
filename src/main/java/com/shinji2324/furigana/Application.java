package com.shinji2324.furigana;

import com.shinji2324.furigana.furigana.FuriganaDocument;
import com.shinji2324.furigana.japanese.ScriptJapaneseTokenizer;
import com.shinji2324.furigana.output.JsonWriter;
import com.shinji2324.furigana.pipeline.FuriganaPipeline;
import com.shinji2324.furigana.subtitle.SubtitleParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ponto de entrada da aplicação (CLI).
 *
 * <p>Uso:
 * <pre>
 * java ... furigana-parser.jar &lt;entrada.srt&gt; &lt;saida.json&gt;
 * </pre>
 *
 * <p>Lê a legenda SRT em UTF-8, executa o pipeline de processamento e persiste
 * o JSON intermediário das entradas com furigana. A integração com os providers
 * de leitura será feita na etapa em que o Kuromoji e o Jisho forem conectados;
 * por enquanto o pipeline roda sem provider (leituras nulas).
 *
 * <p>Esta classe é apenas o ponto de composição: a lógica permanece nas camadas
 * do pipeline. Ela não contém regras de negócio.
 */
public class Application {

    /** Código de saída para erro de argumentos. */
    private static final int EXIT_USAGE = 2;

    /** Código de saída para erro de processamento. */
    private static final int EXIT_ERROR = 1;

    /**
     * Ponto de entrada da CLI.
     *
     * @param args espera dois argumentos: caminho da legenda SRT e caminho de saída JSON
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Uso: furigana-parser <entrada.srt> <saida.json>");
            System.exit(EXIT_USAGE);
            return;
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);

        try {
            String srtContent = Files.readString(input, StandardCharsets.UTF_8);
            FuriganaPipeline pipeline = new FuriganaPipeline(new ScriptJapaneseTokenizer(), null);
            FuriganaDocument document = pipeline.process(srtContent);

            writeJson(output, new JsonWriter().write(document));
            System.out.println("Furigana JSON gerado: " + output.toAbsolutePath());
        } catch (SubtitleParseException e) {
            System.err.println("Legenda inválida: " + e.getMessage());
            System.exit(EXIT_ERROR);
        } catch (IOException e) {
            // JsonProcessingException é subclasse de IOException, portanto é coberta aqui.
            System.err.println("Erro ao processar legenda: " + e.getMessage());
            System.exit(EXIT_ERROR);
        }
    }

    /**
     * Grava o JSON de saída em UTF-8, criando o diretório pai quando necessário.
     */
    private static void writeJson(Path output, String json) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, json, StandardCharsets.UTF_8);
    }
}