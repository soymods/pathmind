package com.pathmind.util;

import com.pathmind.PathmindMod;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Lightweight runtime check for Baritone API availability.
 * We only rely on the mod being loaded and the core API class resolving.
 */
public final class BaritoneDependencyChecker {
    public static final String DOWNLOAD_URL = "https://github.com/cabaletta/baritone/releases/latest";
    private static final String BARITONE_API_CLASS = "baritone.api.BaritoneAPI";
    private static Boolean cachedResult;

    private BaritoneDependencyChecker() {
    }

    /**
     * @return true if the Baritone API mod is loaded and core classes resolve.
     */
    public static boolean isBaritoneApiPresent() {
        if (cachedResult != null) {
            return cachedResult;
        }

        if (!FabricLoader.getInstance().isModLoaded("baritone")) {
            cachedResult = Boolean.FALSE;
            return false;
        }

        try {
            Class.forName(BARITONE_API_CLASS, false, BaritoneDependencyChecker.class.getClassLoader());
            cachedResult = Boolean.TRUE;
        } catch (ClassNotFoundException e) {
            PathmindMod.LOGGER.warn("Baritone API reported as loaded but classes are missing: {}", e.getMessage());
            cachedResult = Boolean.FALSE;
        } catch (LinkageError e) {
            PathmindMod.LOGGER.warn("Baritone API failed to initialize, marking unavailable", e);
            cachedResult = Boolean.FALSE;
        } catch (Throwable t) {
            PathmindMod.LOGGER.warn("Unexpected error while checking for Baritone API", t);
            cachedResult = Boolean.FALSE;
        }

        return cachedResult;
    }

    /**
     * Marks the dependency as unavailable so future checks skip loading attempts.
     */
    public static void markUnavailable() {
        cachedResult = Boolean.FALSE;
    }
}
