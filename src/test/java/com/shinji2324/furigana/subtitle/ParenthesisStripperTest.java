package com.shinji2324.furigana.subtitle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Testes do {@link ParenthesisStripper}.
 */
class ParenthesisStripperTest {

    @Test
    @DisplayName("Remove pares full-width com o conteúdo")
    void removesFullWidthPair() {
        assertEquals("やあ", ParenthesisStripper.strip("やあ（太郎）"));
        assertEquals("やあ", ParenthesisStripper.strip("（太郎）やあ"));
    }

    @Test
    @DisplayName("Remove pares half-width com o conteúdo")
    void removesHalfWidthPair() {
        assertEquals("やあ", ParenthesisStripper.strip("やあ(笑)"));
        assertEquals("やあ", ParenthesisStripper.strip("(Taro)やあ"));
    }

    @Test
    @DisplayName("Remove conteúdo full-width dentro de parênteses half-width")
    void removesFullWidthContentInsideHalfWidthPair() {
        assertEquals("やあ", ParenthesisStripper.strip("やあ(太郎)"));
    }

    @Test
    @DisplayName("Remove vários pares na mesma linha")
    void removesMultiplePairs() {
        assertEquals("やあです", ParenthesisStripper.strip("やあ（太郎）です(笑)"));
    }

    @Test
    @DisplayName("Remove pares aninhados de dentro para fora")
    void removesNestedPairs() {
        assertEquals("やあ", ParenthesisStripper.strip("（外（内）外）やあ"));
    }

    @Test
    @DisplayName("Remove pares vazios ou só com espaços")
    void removesEmptyPairs() {
        assertEquals("やあ", ParenthesisStripper.strip("やあ（）( )"));
    }

    @Test
    @DisplayName("Apara espaços que sobram nas pontas da linha alterada")
    void trimsLeftoverSpaces() {
        assertEquals("やあ", ParenthesisStripper.strip("やあ （笑）"));
        assertEquals("やあ", ParenthesisStripper.strip("（笑） やあ"));
    }

    @Test
    @DisplayName("Preserva o texto quando não há parênteses")
    void keepsTextWithoutParentheses() {
        assertEquals("今日は日本語です。", ParenthesisStripper.strip("今日は日本語です。"));
        assertEquals("スペース は 保つ", ParenthesisStripper.strip("スペース は 保つ"));
    }

    @Test
    @DisplayName("Preserva parênteses sem fechamento correspondente")
    void keepsUnbalancedParenthesis() {
        assertEquals("やあ（太郎", ParenthesisStripper.strip("やあ（太郎"));
        assertEquals("(太郎やあ", ParenthesisStripper.strip("(太郎やあ"));
        assertEquals("やあ）", ParenthesisStripper.strip("やあ（太郎））"));
    }

    @Test
    @DisplayName("Descarta linhas que ficaram em branco e preserva as demais")
    void dropsBlankLines() {
        assertEquals("やあ\n元気", ParenthesisStripper.strip("（笑）\nやあ\n元気\n（ナレ）"));
    }

    @Test
    @DisplayName("Devolve texto vazio quando tudo está entre parênteses")
    void returnsEmptyWhenEverythingIsParenthetical() {
        assertEquals("", ParenthesisStripper.strip("（笑）"));
        assertEquals("", ParenthesisStripper.strip("(laughs)"));
    }

    @Test
    @DisplayName("Trata texto nulo ou vazio")
    void handlesNullOrEmpty() {
        assertNull(ParenthesisStripper.strip(null));
        assertEquals("", ParenthesisStripper.strip(""));
    }
}
