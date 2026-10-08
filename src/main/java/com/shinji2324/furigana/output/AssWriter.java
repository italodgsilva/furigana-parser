package com.shinji2324.furigana.output;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serializador de baixo nível do formato ASS.
 *
 * <p>Monta o arquivo final combinando o cabeçalho ({@link AssTemplate}) com a
 * lista de {@link AssEvent}, além de converter timestamps SRT para o formato ASS.
 * Não conhece o {@code FuriganaDocument} nem fontes de leitura — essa é a
 * responsabilidade do {@link AssGenerator}.
 */
public class AssWriter {

    /** Timestamp SRT: {@code H:MM:SS[,.]mmm}, aceitando 1 a 3 dígitos fracionários. */
    private static final Pattern SRT_TIMESTAMP = Pattern.compile(
            "(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})");

    /** Separador de linha fixo, independente do sistema operacional. */
    private static final String LINE_SEPARATOR = "\n";

    /**
     * Monta o arquivo ASS a partir do cabeçalho e dos eventos.
     *
     * @param template cabeçalho (Script Info, estilos e formato de eventos)
     * @param events   eventos de diálogo, na ordem de exibição
     * @return conteúdo ASS completo, terminado em nova linha
     */
    public String write(AssTemplate template, List<AssEvent> events) {
        StringBuilder builder = new StringBuilder(template.content());
        if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
            builder.append(LINE_SEPARATOR);
        }
        for (AssEvent event : events) {
            builder.append(formatEvent(event)).append(LINE_SEPARATOR);
        }
        return builder.toString();
    }

    /**
     * Formata um evento como uma linha {@code Dialogue:}.
     *
     * @param event evento a formatar
     * @return linha de diálogo do ASS
     */
    public String formatEvent(AssEvent event) {
        return "Dialogue: " + event.layer()
                + "," + event.start()
                + "," + event.end()
                + "," + event.style()
                + ",,0,0,0,,"
                + event.text();
    }

    /**
     * Converte um timestamp SRT para o formato ASS ({@code H:MM:SS.CC}).
     *
     * <p>Exemplos: {@code 00:00:01,000} → {@code 0:00:01.00};
     * {@code 00:01:12,500} → {@code 0:01:12.50}. Timestamps com 1 ou 3 dígitos
     * fracionários também são aceitos (arredondados para centésimos).
     *
     * @param srtTimestamp timestamp no formato SRT
     * @return timestamp no formato ASS
     * @throws IllegalArgumentException se o timestamp não for reconhecido
     */
    public static String formatTime(String srtTimestamp) {
        if (srtTimestamp == null) {
            throw new IllegalArgumentException("Timestamp SRT nulo");
        }
        Matcher matcher = SRT_TIMESTAMP.matcher(srtTimestamp.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Timestamp SRT inválido: " + srtTimestamp);
        }

        int hours = Integer.parseInt(matcher.group(1));
        int minutes = Integer.parseInt(matcher.group(2));
        int seconds = Integer.parseInt(matcher.group(3));
        int centis = toCentiseconds(matcher.group(4));

        if (centis >= 100) {
            centis -= 100;
            seconds++;
            if (seconds >= 60) {
                seconds -= 60;
                minutes++;
                if (minutes >= 60) {
                    minutes -= 60;
                    hours++;
                }
            }
        }

        return String.format("%d:%02d:%02d.%02d", hours, minutes, seconds, centis);
    }

    /**
     * Converte a parte fracionária (1 a 3 dígitos) em centésimos de segundo.
     */
    private static int toCentiseconds(String fraction) {
        int value = Integer.parseInt(fraction);
        return switch (fraction.length()) {
            case 1 -> value * 10;
            case 2 -> value;
            default -> Math.round(value / 10.0f);
        };
    }
}
