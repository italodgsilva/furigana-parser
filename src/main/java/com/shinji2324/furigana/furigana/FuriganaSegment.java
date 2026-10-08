package com.shinji2324.furigana.furigana;

/**
 * Unidade renderizável de uma entrada de furigana.
 *
 * <p>Representa a relação entre o texto original e a sua leitura, sem assumir
 * como o resultado será renderizado (JSON, ASS etc.). Para elementos que não
 * possuem furigana (hiragana, pontuação, espaços), {@link #reading()} é
 * {@code null} — veja o modelo conceitual em docs/architecture.md.
 *
 * @param text    trecho original do texto
 * @param reading leitura do trecho, ou {@code null} quando não há furigana
 */
public record FuriganaSegment(String text, String reading) {
}