package com.shinji2324.furigana.subtitle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link ParenthesisStripper}.
 */
class ParenthesisStripperTest {

    @Test
    @DisplayName("Removes full-width pairs with the content")
    void removesFullWidthPair() {
        assertEquals("やあ", ParenthesisStripper.strip("やあ（太郎）"));
        assertEquals("やあ", ParenthesisStripper.strip("（太郎）やあ"));
    }

    @Test
    @DisplayName("Removes half-width pairs with the content")
    void removesHalfWidthPair() {
        assertEquals("やあ", ParenthesisStripper.strip("やあ(笑)"));
        assertEquals("やあ", ParenthesisStripper.strip("(Taro)やあ"));
    }

    @Test
    @DisplayName("Removes full-width content inside half-width parentheses")
    void removesFullWidthContentInsideHalfWidthPair() {
        assertEquals("やあ", ParenthesisStripper.strip("やあ(太郎)"));
    }

    @Test
    @DisplayName("Removes multiple pairs on the same line")
    void removesMultiplePairs() {
        assertEquals("やあです", ParenthesisStripper.strip("やあ（太郎）です(笑)"));
    }

    @Test
    @DisplayName("Removes nested pairs from the inside out")
    void removesNestedPairs() {
        assertEquals("やあ", ParenthesisStripper.strip("（外（内）外）やあ"));
    }

    @Test
    @DisplayName("Removes empty pairs or pairs with only spaces")
    void removesEmptyPairs() {
        assertEquals("やあ", ParenthesisStripper.strip("やあ（）( )"));
    }

    @Test
    @DisplayName("Trims leftover spaces at the ends of the changed line")
    void trimsLeftoverSpaces() {
        assertEquals("やあ", ParenthesisStripper.strip("やあ （笑）"));
        assertEquals("やあ", ParenthesisStripper.strip("（笑） やあ"));
    }

    @Test
    @DisplayName("Preserves the text when there are no parentheses")
    void keepsTextWithoutParentheses() {
        assertEquals("今日は日本語です。", ParenthesisStripper.strip("今日は日本語です。"));
        assertEquals("スペース は 保つ", ParenthesisStripper.strip("スペース は 保つ"));
    }

    @Test
    @DisplayName("Preserves parentheses without a matching closing one")
    void keepsUnbalancedParenthesis() {
        assertEquals("やあ（太郎", ParenthesisStripper.strip("やあ（太郎"));
        assertEquals("(太郎やあ", ParenthesisStripper.strip("(太郎やあ"));
        assertEquals("やあ）", ParenthesisStripper.strip("やあ（太郎））"));
    }

    @Test
    @DisplayName("Discards lines that became blank and preserves the others")
    void dropsBlankLines() {
        assertEquals("やあ\n元気", ParenthesisStripper.strip("（笑）\nやあ\n元気\n（ナレ）"));
    }

    @Test
    @DisplayName("Returns empty text when everything is between parentheses")
    void returnsEmptyWhenEverythingIsParenthetical() {
        assertEquals("", ParenthesisStripper.strip("（笑）"));
        assertEquals("", ParenthesisStripper.strip("(laughs)"));
    }

    @Test
    @DisplayName("Handles null or empty text")
    void handlesNullOrEmpty() {
        assertNull(ParenthesisStripper.strip(null));
        assertEquals("", ParenthesisStripper.strip(""));
    }
}
