package com.shinji2324.furigana.subtitle;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Remove trechos entre parênteses redondos do texto de uma legenda.
 *
 * <p>Legendas japonesas costumam marcar, entre parênteses, nomes de personagens,
 * leituras alternativas, indicações de cena ou reações (ex.: {@code （笑）}). Como
 * esse conteúdo não faz parte da fala, ele é removido — junto com os próprios
 * parênteses — antes da tokenização, para não gerar furigana nem consultas de
 * leitura.
 *
 * <p>Pares reconhecidos: full-width {@code （…）} e half-width {@code (…)}. Os dois
 * lados de um par devem ser do mesmo tipo; pares aninhados são removidos de
 * dentro para fora. Um parêntese sem fechamento correspondente é preservado.
 *
 * <p>A limpeza é feita linha a linha. Quando uma linha muda, os espaços que
 * sobraram nas pontas são descartados (um espaço residual deslocaria a
 * centralização do ASS, porque o espaço tem largura na grade de caracteres) e
 * linhas que ficaram em branco não entram no resultado. Se todo o texto for
 * consumido, o resultado é uma string vazia — cabe ao chamador decidir se a
 * entrada deve ser descartada.
 */
public final class ParenthesisStripper {

    /** Par full-width: {@code （…）}, sem parênteses internos de nenhum tipo. */
    private static final Pattern FULL_WIDTH = Pattern.compile("（[^（）()]*）");

    /** Par half-width: {@code (…)}, sem parênteses internos de nenhum tipo. */
    private static final Pattern HALF_WIDTH = Pattern.compile("\\([^（）()]*\\)");

    private ParenthesisStripper() {
        // Classe utilitária: não deve ser instanciada.
    }

    /**
     * Remove os trechos entre parênteses redondos e devolve o texto limpo.
     *
     * @param text texto de uma entrada (pode conter quebras de linha), ou {@code null}
     * @return o texto sem os trechos entre parênteses; vazio se tudo for removido
     */
    public static String strip(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            String cleaned = stripLine(line);
            if (!cleaned.equals(line)) {
                cleaned = cleaned.strip();
            }
            if (!cleaned.isBlank()) {
                lines.add(cleaned);
            }
        }
        return String.join("\n", lines);
    }

    /**
     * Remove os pares de uma única linha, repetindo até não haver mais mudança.
     *
     * <p>A repetição é necessária para pares aninhados: o par interno só fica
     * visível (e removível) depois que os de dentro já saíram.
     *
     * @param line linha a limpar
     * @return a linha sem os trechos entre parênteses
     */
    private static String stripLine(String line) {
        if (line.isEmpty()) {
            return line;
        }
        String current = line;
        String previous;
        do {
            previous = current;
            current = FULL_WIDTH.matcher(current).replaceAll("");
            current = HALF_WIDTH.matcher(current).replaceAll("");
        } while (!current.equals(previous));
        return current;
    }
}
