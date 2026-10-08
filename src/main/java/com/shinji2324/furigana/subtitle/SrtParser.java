package com.shinji2324.furigana.subtitle;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser de legendas no formato SRT (SubRip).
 *
 * <p>Formato aceito por bloco:
 * <pre>
 * 1
 * 00:00:01,000 --> 00:00:02,500
 * Texto da legenda
 * </pre>
 *
 * <p>Regras de tolerância e tratamento:
 * <ul>
 *   <li>o índice numérico é opcional (algumas legendas omitem a numeração);</li>
 *   <li>o separador de timestamp aceita espaços extras e vírgula ou ponto;</li>
 *   <li>blocos são separados por linhas em branco; fim de linha CRLF, LF ou CR são aceitos;</li>
 *   <li>linhas de texto são unidas com {@code \n} e preservam espaços estruturais
 *       (inclusive espaços iniciais e finais de cada linha);</li>
 *   <li>um BOM UTF-8 no início do conteúdo é removido.</li>
 * </ul>
 *
 * <p>Política de erro: blocos com uma única linha são ignorados (ruído comum no fim
 * de arquivos), mas blocos maiores que não formam uma entrada válida lançam
 * {@link SubtitleParseException}. O fail-fast evita que uma legenda corrompida
 * dessincronize silenciosamente as entradas seguintes.
 *
 * <p>Esta camada não possui conhecimento sobre japonês, furigana ou fontes de leitura.
 */
public class SrtParser implements SubtitleParser {

    /**
     * Padrão de timestamp SRT: {@code H:MM:SS[,.]mmm}, com vírgula ou ponto e de 1 a 3
     * dígitos fracionários. O separador {@code -->} pode ter espaços ao redor.
     */
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            "(\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3})\\s*-->\\s*(\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3})");

    /** Marca de ordem de byte (BOM) UTF-8, opcionalmente presente no início do arquivo. */
    private static final char UTF_8_BOM = '\uFEFF';

    /** Separador fixo para unir as linhas de texto, independente do sistema operacional. */
    private static final String TEXT_LINE_SEPARATOR = "\n";

    @Override
    public Subtitle parse(String content) {
        Subtitle subtitle = new Subtitle();

        if (content == null || content.isBlank()) {
            return subtitle;
        }

        // Agrupa as linhas em blocos separados por linhas em branco. Essa abordagem
        // é uniforme para LF, CRLF e CR e não depende de um padrão de separação
        // que poderia quebrar a linha de texto em blocos indevidos.
        List<String> currentBlock = new ArrayList<>();
        for (String line : stripBom(content).split("\\R", -1)) {
            if (line.isBlank()) {
                finishBlock(currentBlock, subtitle);
            } else {
                currentBlock.add(line);
            }
        }
        finishBlock(currentBlock, subtitle);
        return subtitle;
    }

    /**
     * Processa o bloco acumulado (se houver) e limpa a lista para o próximo.
     *
     * @param block    linhas do bloco em andamento
     * @param subtitle resultado sendo construído
     */
    private void finishBlock(List<String> block, Subtitle subtitle) {
        if (!block.isEmpty()) {
            parseBlock(block, subtitle);
            block.clear();
        }
    }

    /**
     * Analisa um bloco de legenda e adiciona a entrada correspondente ao resultado.
     *
     * <p>O bloco já chega sem linhas em branco. Ao contrário de um {@code trim} sobre
     * o texto bruto, as linhas não são modificadas, preservando espaços estruturais.
     *
     * @param lines    linhas do bloco
     * @param subtitle resultado sendo construído
     * @throws SubtitleParseException se o bloco não puder ser interpretado como uma entrada
     */
    private void parseBlock(List<String> lines, Subtitle subtitle) {
        String firstLine = lines.get(0);

        // Formato sem índice: a primeira linha já é o timestamp.
        if (isTimestampLine(firstLine)) {
            addEntry(subtitle, null, firstLine, lines, 1);
            return;
        }

        // Uma única linha sem timestamp não contém informação temporal suficiente
        // para formar uma entrada; é tratada como ruído (ex.: marcações no fim do arquivo).
        if (lines.size() == 1) {
            return;
        }

        // Formato padrão: índice + timestamp + texto.
        String timestampLine = lines.get(1);
        if (!isTimestampLine(timestampLine)) {
            throw new SubtitleParseException("Timestamp ausente ou inválido no bloco de legenda:\n"
                    + String.join(TEXT_LINE_SEPARATOR, lines));
        }
        addEntry(subtitle, lines.get(0).trim(), timestampLine, lines, 2);
    }

    /**
     * Verifica se uma linha corresponde a um timestamp SRT (início e fim separados por {@code -->}).
     */
    private boolean isTimestampLine(String line) {
        return TIMESTAMP_PATTERN.matcher(line).matches();
    }

    /**
     * Cria uma {@link SubtitleEntry} a partir das linhas de um bloco e a adiciona ao resultado.
     *
     * @param subtitle      resultado sendo construído
     * @param id            índice da entrada, ou {@code null} quando a legenda não possui numeração
     * @param timestampLine linha contendo o intervalo de tempo
     * @param lines         todas as linhas do bloco
     * @param textStart     índice da primeira linha de texto dentro de {@code lines}
     */
    private void addEntry(Subtitle subtitle, String id, String timestampLine,
                          List<String> lines, int textStart) {
        String[] timestamps = extractTimestamps(timestampLine);
        SubtitleEntry entry = new SubtitleEntry(
                id, timestamps[0], timestamps[1], joinTextLines(lines, textStart));
        subtitle.addEntry(entry);
    }

    /**
     * Extrai o timestamp de início e fim de uma linha no formato {@code início --> fim}.
     *
     * @throws SubtitleParseException se a linha não contém um timestamp válido
     */
    private String[] extractTimestamps(String timestampLine) {
        Matcher matcher = TIMESTAMP_PATTERN.matcher(timestampLine);
        if (!matcher.matches()) {
            throw new SubtitleParseException("Linha de timestamp inválida: " + timestampLine);
        }
        return new String[]{matcher.group(1), matcher.group(2)};
    }

    /**
     * Une as linhas de texto de uma entrada com {@code \n}.
     *
     * <p>As linhas não são limpas nas bordas: espaços estruturais presentes no texto
     * original são preservados.
     */
    private String joinTextLines(List<String> lines, int textStart) {
        return String.join(TEXT_LINE_SEPARATOR, lines.subList(textStart, lines.size()));
    }

    /**
     * Remove um BOM UTF-8 do início do conteúdo, quando presente.
     */
    private String stripBom(String content) {
        if (!content.isEmpty() && content.charAt(0) == UTF_8_BOM) {
            return content.substring(1);
        }
        return content;
    }
}