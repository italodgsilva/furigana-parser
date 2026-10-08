package com.shinji2324.furigana.output;

/**
 * Um evento de diálogo de um arquivo ASS.
 *
 * <p>Os campos {@code start} e {@code end} já devem estar no formato ASS
 * ({@code H:MM:SS.CC}), normalmente obtidos de {@link AssWriter#formatTime(String)}.
 *
 * @param layer camada de exibição (0 é o padrão)
 * @param start timestamp de início no formato ASS
 * @param end   timestamp de fim no formato ASS
 * @param style nome do estilo ({@code Main} ou {@code Furigana})
 * @param text  texto do evento, incluindo as tags de override ({@code \pos} etc.)
 */
public record AssEvent(int layer, String start, String end, String style, String text) {
}
