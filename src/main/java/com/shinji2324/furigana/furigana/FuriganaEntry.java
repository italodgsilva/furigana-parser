package com.shinji2324.furigana.furigana;

import java.util.List;

/**
 * Uma entrada de legenda processada, com furigana.
 *
 * <p>Preserva a identificação e o intervalo de tempo da entrada SRT original
 * (sincronização não é alterada pelo processamento) e mantém o texto original
 * integral, inclusive quebras de linha internas.
 *
 * <p>Os {@link FuriganaSegment}s representam a segmentação anotada do texto. A
 * concatenação dos segmentos reconstrói o conteúdo tokenizado; o campo
 * {@code text} preserva o texto original (com quebras de linha), que é a fonte
 * de verdade para renderização.
 *
 * @param id       índice da entrada na legenda (pode ser {@code null} quando a SRT não possui numeração)
 * @param start    timestamp de início, conforme escrito na SRT (ex.: {@code 00:00:01,000})
 * @param end      timestamp de fim, conforme escrito na SRT
 * @param text     texto original da entrada, com quebras de linha preservadas
 * @param segments lista de segmentos anotados do texto
 */
public record FuriganaEntry(String id, String start, String end, String text, List<FuriganaSegment> segments) {

    /**
     * Construtor compacto: copia a lista de segmentos para garantir imutabilidade.
     */
    public FuriganaEntry {
        segments = List.copyOf(segments);
    }
}