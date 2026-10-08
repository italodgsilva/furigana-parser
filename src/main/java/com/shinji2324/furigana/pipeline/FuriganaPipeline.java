package com.shinji2324.furigana.pipeline;

import com.shinji2324.furigana.furigana.FuriganaDocument;
import com.shinji2324.furigana.furigana.FuriganaEntry;
import com.shinji2324.furigana.furigana.FuriganaSegment;
import com.shinji2324.furigana.japanese.JapaneseTokenizer;
import com.shinji2324.furigana.japanese.Token;
import com.shinji2324.furigana.reading.Reading;
import com.shinji2324.furigana.reading.ReadingProvider;
import com.shinji2324.furigana.subtitle.SrtParser;
import com.shinji2324.furigana.subtitle.Subtitle;
import com.shinji2324.furigana.subtitle.SubtitleEntry;
import com.shinji2324.furigana.subtitle.SubtitleParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orquestrador do pipeline de processamento de legendas.
 *
 * <p>Responsável por conectar as etapas independentes definidas no architecture.md:
 *
 * <pre>
 * SRT → Subtitle Parser → Tokenizador → (leituras opcionais) → FuriganaDocument
 * </pre>
 *
 * <p>Para cada entrada SRT, o pipeline:
 * <ol>
 *   <li>tokeniza o texto original;</li>
 *   <li>consulta o {@link ReadingProvider} quando um está configurado
 *       (o provider é opcional — sem ele, as leituras ficam {@code null});</li>
 *   <li>produz um {@link FuriganaEntry} preservando id e timestamps, com um
 *       {@link FuriganaSegment} por token e o texto original integral.</li>
 * </ol>
 *
 * <p>Entradas sem kanji (apenas kana/pontuação/espaços) não são descartadas: elas
 * continuam presentes no documento, com segmentos e leituras nulas. Isso evita que
 * a sincronização da legenda seja corrompida na saída.
 *
 * <p>A camada {@code pipeline} não depende de nenhum formato de saída específico
 * (JSON, ASS etc.).
 */
public class FuriganaPipeline {

    private final SubtitleParser subtitleParser;
    private final JapaneseTokenizer tokenizer;
    private final ReadingProvider readingProvider;

    /**
     * Cria o pipeline com o {@link SrtParser} padrão.
     *
     * @param tokenizer       tokenizador de texto japonês
     * @param readingProvider provider de leituras, ou {@code null} para processar sem leituras
     */
    public FuriganaPipeline(JapaneseTokenizer tokenizer, ReadingProvider readingProvider) {
        this(new SrtParser(), tokenizer, readingProvider);
    }

    /**
     * Cria o pipeline com um parser de legendas informado.
     *
     * @param subtitleParser  parser de legendas (ex.: {@link SrtParser})
     * @param tokenizer       tokenizador de texto japonês
     * @param readingProvider provider de leituras, ou {@code null} para processar sem leituras
     */
    public FuriganaPipeline(SubtitleParser subtitleParser,
                            JapaneseTokenizer tokenizer,
                            ReadingProvider readingProvider) {
        this.subtitleParser = subtitleParser;
        this.tokenizer = tokenizer;
        this.readingProvider = readingProvider;
    }

    /**
     * Processa o conteúdo de uma legenda SRT.
     *
     * @param srtContent conteúdo da legenda (UTF-8)
     * @return documento intermediário com as entradas processadas
     */
    public FuriganaDocument process(String srtContent) {
        return build(subtitleParser.parse(srtContent));
    }

    /**
     * Processa um arquivo de legenda SRT (leitura em UTF-8).
     *
     * @param srtFile caminho do arquivo SRT
     * @return documento intermediário com as entradas processadas
     * @throws IOException se o arquivo não puder ser lido
     */
    public FuriganaDocument process(Path srtFile) throws IOException {
        return process(Files.readString(srtFile, StandardCharsets.UTF_8));
    }

    /**
     * Converte cada entrada da legenda em uma {@link FuriganaEntry}.
     */
    private FuriganaDocument build(Subtitle subtitle) {
        FuriganaDocument document = new FuriganaDocument();
        for (SubtitleEntry entry : subtitle.getEntries()) {
            document.addEntry(toFuriganaEntry(entry));
        }
        return document;
    }

    /**
     * Tokeniza o texto de uma entrada e associa as leituras aos tokens.
     *
     * <p>Na etapa atual, cada token corresponde a um segmento e a leitura vem
     * diretamente do provider quando disponível. A segmentação de kanji +
     * okurigana dentro de um token (ex.: 勉強し → 勉強/し) será feita quando o
     * tokenizador morfológico (Kuromoji) for integrado.
     */
    private FuriganaEntry toFuriganaEntry(SubtitleEntry entry) {
        List<FuriganaSegment> segments = new ArrayList<>();
        for (Token token : tokenizer.tokenize(entry.getText())) {
            String reading = findReading(token).map(Reading::getReading).orElse(null);
            segments.add(new FuriganaSegment(token.getSurface(), reading));
        }
        return new FuriganaEntry(entry.getId(), entry.getStart(), entry.getEnd(), entry.getText(), segments);
    }

    /**
     * Consulta a leitura de um token, retornando vazio quando não há provider
     * configurado ou quando ele não encontra uma leitura.
     */
    private Optional<Reading> findReading(Token token) {
        if (readingProvider == null) {
            return Optional.empty();
        }
        return readingProvider.findReading(token);
    }
}