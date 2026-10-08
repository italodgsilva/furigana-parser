package com.shinji2324.furigana.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes do {@link CliParser}.
 */
class CliParserTest {

    private final CliParser parser = new CliParser();

    @Test
    @DisplayName("Sem argumentos ou com ajuda retorna o modo HELP")
    void returnsHelpMode() {
        assertEquals(CliCommand.Mode.HELP, parser.parse(new String[] {}).mode());
        assertEquals(CliCommand.Mode.HELP, parser.parse(new String[] {"--help"}).mode());
        assertEquals(CliCommand.Mode.HELP, parser.parse(new String[] {"-h"}).mode());
        assertEquals(CliCommand.Mode.HELP, parser.parse(new String[] {"help"}).mode());
        assertNull(parser.parse(new String[] {}).input());
    }

    @Test
    @DisplayName("Interpreta a geração de JSON")
    void parsesJsonGeneration() {
        CliCommand command = parser.parse(new String[] {"entrada.srt", "saida.json"});

        assertEquals(CliCommand.Mode.GENERATE_JSON, command.mode());
        assertEquals(Path.of("entrada.srt"), command.input());
        assertEquals(Path.of("saida.json"), command.output());
    }

    @Test
    @DisplayName("Interpreta a geração de ASS (extensão sem distinção de maiúsculas)")
    void parsesAssGeneration() {
        CliCommand command = parser.parse(new String[] {"entrada.srt", "SAIDA.ASS"});

        assertEquals(CliCommand.Mode.GENERATE_ASS, command.mode());
        assertEquals(Path.of("SAIDA.ASS"), command.output());
    }

    @Test
    @DisplayName("Interpreta o modo de reprodução --mpv e o alias play")
    void parsesPlayMode() {
        CliCommand command = parser.parse(new String[] {"--mpv", "legenda.srt", "video.mkv"});
        assertEquals(CliCommand.Mode.PLAY, command.mode());
        assertEquals(Path.of("legenda.srt"), command.input());
        assertEquals(Path.of("video.mkv"), command.output());

        assertEquals(CliCommand.Mode.PLAY,
                parser.parse(new String[] {"play", "legenda.srt", "video.mkv"}).mode());
    }

    @Test
    @DisplayName("Rejeita extensão de saída não suportada")
    void rejectsUnsupportedExtension() {
        assertThrows(CliUsageException.class,
                () -> parser.parse(new String[] {"entrada.srt", "saida.txt"}));
    }

    @Test
    @DisplayName("Rejeita quantidade de argumentos inválida")
    void rejectsWrongArgumentCount() {
        assertThrows(CliUsageException.class,
                () -> parser.parse(new String[] {"entrada.srt"}));
        assertThrows(CliUsageException.class,
                () -> parser.parse(new String[] {"a", "b", "c"}));
        assertThrows(CliUsageException.class,
                () -> parser.parse(new String[] {"--mpv", "legenda.srt"}));
    }
}
