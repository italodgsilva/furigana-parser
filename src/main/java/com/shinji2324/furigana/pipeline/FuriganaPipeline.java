package com.shinji2324.furigana.pipeline;

import com.shinji2324.furigana.furigana.FuriganaDocument;
import com.shinji2324.furigana.furigana.FuriganaEntry;
import com.shinji2324.furigana.furigana.FuriganaSegment;
import com.shinji2324.furigana.furigana.FuriganaSegmenter;
import com.shinji2324.furigana.japanese.JapaneseScript;
import com.shinji2324.furigana.japanese.JapaneseTokenizer;
import com.shinji2324.furigana.japanese.Token;
import com.shinji2324.furigana.reading.Reading;
import com.shinji2324.furigana.reading.ReadingProvider;
import com.shinji2324.furigana.subtitle.ParenthesisStripper;
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
 *   <li>resolve a leitura de cada token: a leitura do próprio tokenizador tem
 *       prioridade (Kuromoji) e, quando ausente, consulta o {@link ReadingProvider}
 *       — apenas para tokens que contêm kanji;</li>
 *   <li>separa cada token em kanji (com furigana) e okurigana (sem furigana) com
 *       o {@link FuriganaSegmenter}, preservando id e timestamps.</li>
 * </ol>
 *
 * <p>Entradas sem kanji (apenas kana/pontuação/espaços) não são descartadas: elas
 * continuam presentes no documento, com segmentos sem leitura. Isso evita que a
 * sincronização da legenda seja corrompida na saída.
 *
 * <p>Opcionalmente, o texto de cada entrada é filtrado com o
 * {@link ParenthesisStripper} antes da tokenização, removendo os trechos entre
 * parênteses. Nesse modo, entradas que ficam inteiramente vazias (ex.: uma linha
 * que era só {@code （笑）}) são descartadas — é a única situação em que uma
 * entrada deixa de existir.
 *
 * <p>A camada {@code pipeline} não depende de nenhum formato de saída específico
 * (JSON, ASS etc.).
 */
public class FuriganaPipeline {

    private final SubtitleParser subtitleParser;
    private final JapaneseTokenizer tokenizer;
    private final ReadingProvider readingProvider;
    private final FuriganaSegmenter segmenter;
    private final boolean stripParentheses;

    /**
     * Cria o pipeline com o {@link SrtParser} padrão.
     *
     * @param tokenizer       tokenizador de texto japonês
     * @param readingProvider provider de leituras, ou {@code null} para processar sem leituras
     */
    public FuriganaPipeline(JapaneseTokenizer tokenizer, ReadingProvider readingProvider) {
        this(tokenizer, readingProvider, false);
    }

    /**
     * Cria o pipeline com o {@link SrtParser} padrão e a limpeza opcional.
     *
     * @param tokenizer        tokenizador de texto japonês
     * @param readingProvider  provider de leituras, ou {@code null} para processar sem leituras
     * @param stripParentheses se os trechos entre parênteses devem ser removidos do texto
     */
    public FuriganaPipeline(JapaneseTokenizer tokenizer, ReadingProvider readingProvider,
                            boolean stripParentheses) {
        this(new SrtParser(), tokenizer, readingProvider, stripParentheses);
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
        this(subtitleParser, tokenizer, readingProvider, false);
    }

    /**
     * Cria o pipeline com um parser de legendas informado e a limpeza opcional.
     *
     * @param subtitleParser   parser de legendas (ex.: {@link SrtParser})
     * @param tokenizer        tokenizador de texto japonês
     * @param readingProvider  provider de leituras, ou {@code null} para processar sem leituras
     * @param stripParentheses se os trechos entre parênteses devem ser removidos do texto
     */
    public FuriganaPipeline(SubtitleParser subtitleParser,
                            JapaneseTokenizer tokenizer,
                            ReadingProvider readingProvider,
                            boolean stripParentheses) {
        this.subtitleParser = subtitleParser;
        this.tokenizer = tokenizer;
        this.readingProvider = readingProvider;
        this.segmenter = new FuriganaSegmenter();
        this.stripParentheses = stripParentheses;
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
     *
     * <p>Quando a limpeza de parênteses está ligada, o texto é filtrado antes da
     * tokenização (evitando leituras para o conteúdo removido) e entradas que
     * ficam inteiramente vazias — por exemplo, uma linha que era só {@code （笑）}
     * — não entram no documento. As demais mantêm id e timestamps originais.
     */
    private FuriganaDocument build(Subtitle subtitle) {
        FuriganaDocument document = new FuriganaDocument();
        for (SubtitleEntry entry : subtitle.getEntries()) {
            String text = entry.getText();
            if (stripParentheses) {
                text = ParenthesisStripper.strip(text);
                if (text.isBlank()) {
                    continue;
                }
            }
            document.addEntry(toFuriganaEntry(entry, text));
        }
        return document;
    }

    /**
     * Tokeniza o texto de uma entrada, resolve as leituras e gera os segmentos.
     *
     * @param entry entrada original (fonte de id e timestamps)
     * @param text  texto já limpo, que será tokenizado e preservado na entrada
     */
    private FuriganaEntry toFuriganaEntry(SubtitleEntry entry, String text) {
        List<FuriganaSegment> segments = new ArrayList<>();
        for (Token token : tokenizer.tokenize(text)) {
            segments.addAll(segmenter.segment(token.getSurface(), resolveReading(token)));
        }
        return new FuriganaEntry(entry.getId(), entry.getStart(), entry.getEnd(), text, segments);
    }

    /**
     * Determina a leitura de um token.
     *
     * <p>A leitura fornecida pelo tokenizador (Kuromoji) tem prioridade. Sem ela,
     * consulta o provider apenas quando o token contém kanji — kana, pontuação e
     * latim não precisam de furigana e não devem gerar consultas externas.
     *
     * @param token token a resolver
     * @return leitura em hiragana, ou {@code null} quando não há
     */
    private String resolveReading(Token token) {
        if (token.hasReading()) {
            return token.getReading();
        }
        if (readingProvider == null || !JapaneseScript.containsKanji(token.getSurface())) {
            return null;
        }
        Optional<Reading> reading = readingProvider.findReading(token);
        return reading.map(Reading::getReading).orElse(null);
    }
}
