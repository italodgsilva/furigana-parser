package com.shinji2324.furigana.mpv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Integração com o MPV para reprodução de um vídeo com a legenda de furigana.
 *
 * <p>Esta camada é deliberadamente simples e desacoplada do pipeline: ela apenas
 * monta e executa o comando
 * {@code mpv --sub-file=<legenda.ass> <video>}. Não conhece SRT, Kuromoji, Jisho
 * nem o {@code FuriganaDocument}. O caminho da legenda gerada é responsabilidade
 * de quem chama (ver {@link MpvPaths}).
 *
 * <p>A execução real do processo é delegada a um {@link ProcessRunner}, o que
 * permite testar a integração sem abrir o player.
 *
 * <p>Pressupõe que o executável {@code mpv} esteja instalado e disponível no
 * {@code PATH}.
 */
public class MpvIntegration {

    /** Nome do executável do MPV procurado no {@code PATH}. */
    public static final String MPV_EXECUTABLE = "mpv";

    private final ProcessRunner processRunner;

    /** Cria a integração com o executor de processos padrão. */
    public MpvIntegration() {
        this(new DefaultProcessRunner());
    }

    /**
     * Cria a integração com um executor de processos informado.
     *
     * @param processRunner executor usado para iniciar o MPV
     */
    public MpvIntegration(ProcessRunner processRunner) {
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
    }

    /**
     * Monta o comando do MPV que abre o vídeo com a legenda de furigana.
     *
     * @param video         arquivo de vídeo
     * @param subtitleFile  arquivo ASS com o furigana
     * @return linha de comando pronta para execução
     */
    public List<String> buildCommand(Path video, Path subtitleFile) {
        Objects.requireNonNull(video, "video");
        Objects.requireNonNull(subtitleFile, "subtitleFile");
        return List.of(
                MPV_EXECUTABLE,
                "--sub-file=" + subtitleFile.toAbsolutePath(),
                video.toAbsolutePath().toString());
    }

    /**
     * Abre o vídeo no MPV com a legenda de furigana e aguarda o término.
     *
     * @param video        arquivo de vídeo
     * @param subtitleFile arquivo ASS com o furigana
     * @return código de saída do MPV
     * @throws IOException          se o MPV não puder ser iniciado (ex.: não instalado)
     * @throws InterruptedException se a thread for interrompida enquanto aguarda
     */
    public int open(Path video, Path subtitleFile) throws IOException, InterruptedException {
        return processRunner.run(buildCommand(video, subtitleFile));
    }
}
