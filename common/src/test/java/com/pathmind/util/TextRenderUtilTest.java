package com.pathmind.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextRenderUtilTest {

    @Test
    void wrapsAtWordBoundaries() {
        assertEquals(
            List.of("Editable", "fields live", "inside"),
            TextRenderUtil.wrapWords("Editable fields live inside", 11, String::length)
        );
    }

    @Test
    void onlySplitsAWordWhenTheWordCannotFitOnItsOwnLine() {
        assertEquals(
            List.of("small", "extra", "ordin", "arily", "wide"),
            TextRenderUtil.wrapWords("small extraordinarily wide", 5, String::length)
        );
    }

    @Test
    void normalizesWhitespaceAndPreservesExplicitLineBreaks() {
        assertEquals(
            List.of("one two", "three", "", "four"),
            TextRenderUtil.wrapWords(" one   two three\n\n four ", 7, String::length)
        );
    }

    @Test
    void doesNotSplitUnicodeSurrogatePairs() {
        assertEquals(
            List.of("😀", "😀"),
            TextRenderUtil.wrapWords("😀😀", 1, value -> value.codePointCount(0, value.length()))
        );
    }
}
