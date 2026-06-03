package com.pathmind.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyVariableSyntaxCompatTest {

    @Test
    void convertsLegacyVariableReferencesToDollarSyntax() {
        assertEquals("$length", LegacyVariableSyntaxCompat.normalizeLegacyVariableSyntax("~length"));
        assertEquals("position=$target", LegacyVariableSyntaxCompat.normalizeLegacyVariableSyntax("position=~target"));
        assertEquals("$lhs + $rhs", LegacyVariableSyntaxCompat.normalizeLegacyVariableSyntax("~lhs + ~rhs"));
    }

    @Test
    void leavesRelativeCoordinatesAndRegularTildesUntouched() {
        assertEquals("~ ~1 ~-3", LegacyVariableSyntaxCompat.normalizeLegacyVariableSyntax("~ ~1 ~-3"));
        assertEquals("hello~world", LegacyVariableSyntaxCompat.normalizeLegacyVariableSyntax("hello~world"));
    }
}
