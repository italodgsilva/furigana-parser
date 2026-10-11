package com.shinji2324.furigana.output;

import com.shinji2324.furigana.font.FontFile;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Embute arquivos de fonte em um ASS por meio da seção {@code [Fonts]}.
 *
 * <p>A seção é o mecanismo de fontes embutidas do próprio formato ASS: cada
 * bloco começa com {@code fontname: <arquivo>} seguido dos bytes da fonte
 * codificados. Quando o player encontra a seção, ele registra essas fontes no
 * provedor do sistema, o que dispensa instalar a fonte na máquina.
 *
 * <p>A codificação é a mesma implementada pelo libass: cada 3 bytes viram 4
 * caracteres, com 6 bits por caractere somados a 33 ({@code '!'}). As quebras de
 * linha são ignoradas na leitura, então a codificação é distribuída em linhas de
 * tamanho fixo. O total de caracteres nunca deixa resto 1 na divisão por 4 — o
 * libass rejeita esse caso.
 */
public final class AssFontEmbedder {

    /** Quantidade de caracteres codificados por linha (as quebras são ignoradas). */
    private static final int LINE_LENGTH = 80;

    /** Cabeçalho da seção de eventos, onde a seção {@code [Fonts]} é inserida. */
    private static final Pattern EVENTS_SECTION = Pattern.compile("(?m)^\\[Events\\][ \\t]*\\R?");

    /**
     * Insere as fontes informadas no ASS, antes da seção {@code [Events]}.
     *
     * @param ass   conteúdo ASS já montado
     * @param fonts fontes a embutir
     * @return o ASS com a seção {@code [Fonts]} (ou o próprio ASS, se não houver
     *         fontes)
     */
    public String embed(String ass, List<FontFile> fonts) {
        if (ass == null || fonts == null || fonts.isEmpty()) {
            return ass;
        }
        String section = section(fonts);
        Matcher matcher = EVENTS_SECTION.matcher(ass);
        if (!matcher.find()) {
            return ass.endsWith("\n") ? ass + section : ass + "\n" + section;
        }
        return ass.substring(0, matcher.start()) + section + "\n" + ass.substring(matcher.start());
    }

    /**
     * Monta a seção {@code [Fonts]} com todas as fontes informadas.
     *
     * @param fonts fontes a codificar
     * @return texto da seção, terminado em nova linha
     */
    public static String section(List<FontFile> fonts) {
        StringBuilder builder = new StringBuilder("[Fonts]\n");
        for (FontFile font : fonts) {
            builder.append("fontname: ").append(font.fileName()).append('\n');
            builder.append(uuencode(font.data(), LINE_LENGTH));
        }
        return builder.toString();
    }

    /**
     * Codifica bytes no formato de fontes do ASS (uuencode do libass).
     *
     * @param data       bytes a codificar
     * @param lineLength quantidade de caracteres por linha (ignorada na leitura)
     * @return texto codificado, terminado em nova linha
     */
    static String uuencode(byte[] data, int lineLength) {
        StringBuilder builder = new StringBuilder(data.length / 3 * 4 + data.length / lineLength + 2);
        int column = 0;
        for (int i = 0; i < data.length; i += 3) {
            int remaining = data.length - i;
            int b0 = data[i] & 0xFF;
            int b1 = remaining > 1 ? data[i + 1] & 0xFF : 0;
            int b2 = remaining > 2 ? data[i + 2] & 0xFF : 0;
            int value = (b0 << 16) | (b1 << 8) | b2;
            int chars = Math.min(remaining, 3) + 1; // 3→4, 2→3, 1→2
            for (int k = 0; k < chars; k++) {
                builder.append((char) (((value >>> (18 - 6 * k)) & 63) + 33));
                if (++column == lineLength) {
                    builder.append('\n');
                    column = 0;
                }
            }
        }
        if (column != 0) {
            builder.append('\n');
        }
        return builder.toString();
    }
}
