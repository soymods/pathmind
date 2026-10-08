package com.pathmind.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The check exists to stop a model justifying intent with older chat or tool output. It should not
 * also fail on the ways a model legitimately reproduces a quote.
 */
class AiIntentEvidenceTest {
    private static final String REQUEST = "make a thing to mine a 2x2 hole until you find diamonds,\n"
        + "stop when you find diamonds and don't tell anyone";

    @Test void acceptsAnExactQuote() {
        assertTrue(AiPresetAgent.quotesRequest(REQUEST, "mine a 2x2 hole until you find diamonds"));
    }

    @Test void acceptsQuotesThatDifferOnlyInPresentation() {
        assertTrue(AiPresetAgent.quotesRequest(REQUEST, "Mine A 2x2 Hole"), "capitalisation");
        assertTrue(AiPresetAgent.quotesRequest(REQUEST, "diamonds,  stop when you find diamonds"), "collapsed whitespace");
        assertTrue(AiPresetAgent.quotesRequest(REQUEST, "don’t tell anyone"), "typographic apostrophe");
        assertTrue(AiPresetAgent.quotesRequest(REQUEST, "  stop when you find diamonds  "), "surrounding space");
    }

    @Test void stillRejectsEvidenceThatIsNotInThisRequest() {
        assertFalse(AiPresetAgent.quotesRequest(REQUEST, "build a cobblestone generator"));
        assertFalse(AiPresetAgent.quotesRequest(REQUEST, "Returned 12 exact node contract(s)"));
    }

    @Test void rejectsMissingOrEmptyEvidence() {
        assertFalse(AiPresetAgent.quotesRequest(REQUEST, null));
        assertFalse(AiPresetAgent.quotesRequest(REQUEST, "   "));
        assertFalse(AiPresetAgent.quotesRequest(null, "anything"));
    }
}
