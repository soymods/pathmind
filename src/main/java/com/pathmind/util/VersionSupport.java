package com.pathmind.util;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Central source of truth for supported Minecraft versions.
 */
public final class VersionSupport {
    public static final String MIN_VERSION = "1.21";
    public static final String MAX_VERSION = "1.21.8";
    public static final List<String> SUPPORTED_VERSIONS = List.of(
        "1.21",
        "1.21.1",
        "1.21.2",
        "1.21.3",
        "1.21.4",
        "1.21.5",
        "1.21.6",
        "1.21.7",
        "1.21.8"
    );
    public static final String SUPPORTED_RANGE = MIN_VERSION + " - " + MAX_VERSION;

    private VersionSupport() {
    }

    public static boolean isSupported(String version) {
        if (version == null) {
            return false;
        }
        String normalized = version.toLowerCase(Locale.ROOT).trim();
        return SUPPORTED_VERSIONS.stream().anyMatch(v -> v.equalsIgnoreCase(normalized));
    }
}
