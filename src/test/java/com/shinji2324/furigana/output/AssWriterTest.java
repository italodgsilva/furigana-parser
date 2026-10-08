package com.shinji2324.furigana.output;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do {@link AssWriter} (formatação de tempo e eventos).
 */
class AssWriterTest {

    private final AssWriter writer = new AssWriter();

    @Test
    @DisplayName("Converte timestamps SRT para o formato ASS")
    void convertsSrtTimestamps() {
        assertEquals("0:00:01.00", AssWriter.formatTime("00:00:01,000"));
        assertEquals("0:01:12.50", AssWriter.formatTime("00:01:12,500"));
        assertEquals("1:02:03.40", AssWriter.formatTime("1:02:03,4"));
        assertEquals("0:00:00.90", AssWriter.formatTime("00:00:00.900"));
    }

    @Test
    @DisplayName("Arredonda milissegundos e propaga o transporte de centésimos")
    void roundsAndCarriesCentiseconds() {
        assertEquals("0:00:01.00", AssWriter.formatTime("00:00:00,999"));
        assertEquals("0:01:00.00", AssWriter.formatTime("00:00:59,995"));
    }

    @Test
    @DisplayName("Rejeita timestamp inválido")
    void rejectsInvalidTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> AssWriter.formatTime("00:00:01"));
        assertThrows(IllegalArgumentException.class, () -> AssWriter.formatTime(null));
    }

    @Test
    @DisplayName("Formata um evento como linha Dialogue")
    void formatsEventLine() {
        AssEvent event = new AssEvent(0, "0:00:01.00", "0:00:04.50", "Main", "olá");

        assertEquals("Dialogue: 0,0:00:01.00,0:00:04.50,Main,,0,0,0,,olá", writer.formatEvent(event));
    }

    @Test
    @DisplayName("Anexa os eventos ao cabeçalho, garantindo a quebra de linha final")
    void appendsEventsToTemplate() {
        AssTemplate template = new AssTemplate("CABECALHO\n");
        String output = writer.write(template, List.of(
                new AssEvent(0, "0:00:01.00", "0:00:02.00", "Main", "primeiro"),
                new AssEvent(0, "0:00:01.00", "0:00:02.00", "Furigana", "segundo")));

        assertEquals("CABECALHO\n"
                + "Dialogue: 0,0:00:01.00,0:00:02.00,Main,,0,0,0,,primeiro\n"
                + "Dialogue: 0,0:00:01.00,0:00:02.00,Furigana,,0,0,0,,segundo\n", output);
    }

    @Test
    @DisplayName("Não emite eventos quando a lista está vazia")
    void writesTemplateOnlyWithoutEvents() {
        String output = writer.write(new AssTemplate("CABECALHO\n"), List.of());

        assertEquals("CABECALHO\n", output);
        assertTrue(writer.write(new AssTemplate("CABECALHO"), List.of()).startsWith("CABECALHO"));
    }
}
