package com.shinji2324.furigana.mpv;

import java.nio.file.Path;

/**
 * Convenções de nomes de arquivo usadas na integração com o MPV.
 *
 * <p>A legenda de furigana é gerada ao lado da SRT de origem, com o sufixo
 * {@code .furigana.ass}. Exemplos:
 *
 * <pre>
 * episodio.srt     → episodio.furigana.ass
 * episodio.ja.srt  → episodio.ja.furigana.ass
 * sem-extensao     → sem-extensao.furigana.ass
 * </pre>
 */
public final class MpvPaths {

    /** Sufixo e extensão do arquivo ASS gerado. */
    public static final String FURIGANA_SUFFIX = ".furigana.ass";

    private MpvPaths() {
        // Classe utilitária: não deve ser instanciada.
    }

    /**
     * Deriva o caminho da legenda de furigana a partir da SRT de origem.
     *
     * <p>Remove a extensão do nome do arquivo e acrescenta
     * {@value #FURIGANA_SUFFIX}, mantendo o mesmo diretório.
     *
     * @param srt caminho da legenda SRT
     * @return caminho do ASS a ser gerado
     */
    public static Path furiganaSubtitle(Path srt) {
        String name = srt.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return srt.resolveSibling(base + FURIGANA_SUFFIX);
    }
}
