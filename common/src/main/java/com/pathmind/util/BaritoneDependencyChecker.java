package com.pathmind.util;

import dev.architectury.platform.Platform;

/**
 * Lightweight runtime check for Baritone API availability.
 * We only rely on the mod being loaded and the core API class resolving.
 */
public final class BaritoneDependencyChecker {
    public static final String DOWNLOAD_URL = "https://github.com/cabaletta/baritone/releases/latest";
    private static final String BARITONE_MOD_ID = "baritone";
    private static final String BARITONE_API_CLASS = "baritone.api.BaritoneAPI";
    private static Boolean cachedApiResult;
    private static Boolean cachedModResult;

    private BaritoneDependencyChecker() {
    }

    /**
     * @return true if the Baritone API mod is loaded and core classes resolve.
     */
    public static boolean isBaritoneApiPresent() {
        if (cachedApiResult != null) {
            return cachedApiResult;
        }

        if (!isBaritonePresent()) {
            cachedApiResult = Boolean.FALSE;
            return false;
        }

        try {
            Class.forName(BARITONE_API_CLASS, false, BaritoneDependencyChecker.class.getClassLoader());
            cachedApiResult = Boolean.TRUE;
        } catch (Throwable ignored) {
            cachedApiResult = Boolean.FALSE;
        }

        return cachedApiResult;
    }

    /**
     * @return true if any Baritone mod is loaded (API may or may not be present).
     */
    public static boolean isBaritonePresent() {
        if (cachedModResult != null) {
            return cachedModResult;
        }
        try {
            if (Platform.isModLoaded(BARITONE_MOD_ID)) {
                cachedModResult = Boolean.TRUE;
                return cachedModResult;
            }
        } catch (Throwable ignored) {
            // Platform not initialized (e.g., unit tests) - fall through to class-based detection.
        }

        // Relaxed detection for Baritone variants (e.g., meteor-bundled) via class presence
        try {
            Class.forName(BARITONE_API_CLASS, false, BaritoneDependencyChecker.class.getClassLoader());
            cachedModResult = Boolean.TRUE;
        } catch (Throwable ignored) {
            cachedModResult = Boolean.FALSE;
        }
        return cachedModResult;
    }

    /**
     * Marks the dependency as unavailable so future checks skip loading attempts.
     */
    public static void markUnavailable() {
        cachedApiResult = Boolean.FALSE;
    }
}
