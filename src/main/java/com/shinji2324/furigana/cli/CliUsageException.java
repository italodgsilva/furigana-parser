package com.shinji2324.furigana.cli;

/**
 * Erro de uso da linha de comando (argumentos ausentes ou inválidos).
 *
 * <p>Sinaliza que a aplicação deve imprimir as instruções de uso e encerrar com
 * código de erro de argumentos, sem tentar processar nada.
 */
public class CliUsageException extends RuntimeException {

    /**
     * Cria a exceção com a mensagem de uso.
     *
     * @param message mensagem exibida ao usuário
     */
    public CliUsageException(String message) {
        super(message);
    }
}
