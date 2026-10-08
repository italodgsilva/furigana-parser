package com.shinji2324.furigana.subtitle;

/**
 * Exceção lançada quando um conteúdo no formato SRT não pode ser interpretado.
 *
 * <p>É utilizado para sinalizar erros de estrutura (timestamp ausente ou inválido),
 * permitindo que o chamador decida como tratar o problema em vez de uma entrada
 * ser silenciosamente descartada.
 */
public class SubtitleParseException extends RuntimeException {

    /**
     * Cria uma exceção com a mensagem de detalhe informada.
     *
     * @param message descrição do erro
     */
    public SubtitleParseException(String message) {
        super(message);
    }

    /**
     * Cria uma exceção com mensagem e causa raiz.
     *
     * @param message descrição do erro
     * @param cause   causa do erro
     */
    public SubtitleParseException(String message, Throwable cause) {
        super(message, cause);
    }
}