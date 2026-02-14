package com.pathmind.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionSupportTest {

    @Test
    void supportedRangeMatchesBoundaryConstants() {
        assertEquals(VersionSupport.MIN_VERSION + " - " + VersionSupport.MAX_VERSION, VersionSupport.SUPPORTED_RANGE);
    }

    @Test
    void isSupportedAcceptsKnownVersionsAndNormalization() {
        assertTrue(VersionSupport.isSupported("1.21"));
        assertTrue(VersionSupport.isSupported(" 1.21.11 "));
        assertTrue(VersionSupport.isSupported("1.21.10"));
    }

    @Test
    void isSupportedRejectsUnknownOrNullVersions() {
        assertFalse(VersionSupport.isSupported(null));
        assertFalse(VersionSupport.isSupported(""));
        assertFalse(VersionSupport.isSupported("1.20.6"));
        assertFalse(VersionSupport.isSupported("2.0"));
    }
}
