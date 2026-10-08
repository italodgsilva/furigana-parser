package com.shinji2324.furigana.output;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Cabeçalho estático de um arquivo ASS (Script Info, estilos e formato de eventos).
 *
 * <p>Mantém a parte visual separada da lógica de geração, conforme o
 * docs/ass-format.md: cores, fontes, tamanhos e resolução podem ser alterados no
 * recurso {@code /ass/default-style.ass} sem tocar no {@link AssGenerator}.
 *
 * <p><strong>Atenção:</strong> o {@link AssLayout} assume a resolução declarada
 * aqui ({@code PlayResX}/{@code PlayResY} = 1920x1080). Alterar a resolução no
 * template exige ajustar as constantes de layout.
 */
public final class AssTemplate {

    /** Caminho do template padrão no classpath. */
    public static final String DEFAULT_RESOURCE = "/ass/default-style.ass";

    private final String content;

    /**
     * Cria um template a partir de um conteúdo já carregado.
     *
     * @param content cabeçalho ASS (até a linha {@code Format:} de eventos)
     */
    public AssTemplate(String content) {
        this.content = content == null ? "" : content;
    }

    /**
     * Carrega o template padrão do classpath.
     *
     * @return o template embutido no recurso {@link #DEFAULT_RESOURCE}
     * @throws IllegalStateException se o recurso não puder ser lido
     */
    public static AssTemplate defaultTemplate() {
        try (InputStream stream = AssTemplate.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Recurso ASS não encontrado: " + DEFAULT_RESOURCE);
            }
            return new AssTemplate(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o template ASS: " + DEFAULT_RESOURCE, e);
        }
    }

    /**
     * Retorna o conteúdo do template.
     *
     * @return cabeçalho ASS
     */
    public String content() {
        return content;
    }
}
