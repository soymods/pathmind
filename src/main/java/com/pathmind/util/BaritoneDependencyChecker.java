package com.pathmind.util;

import com.pathmind.PathmindMod;
import net.fabricmc.loader.api.FabricLoader;

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
        } catch (ClassNotFoundException e) {
            PathmindMod.LOGGER.warn("Baritone API reported as loaded but classes are missing: {}", e.getMessage());
            cachedApiResult = Boolean.FALSE;
        } catch (LinkageError e) {
            PathmindMod.LOGGER.warn("Baritone API failed to initialize, marking unavailable", e);
            cachedApiResult = Boolean.FALSE;
        } catch (Throwable t) {
            PathmindMod.LOGGER.warn("Unexpected error while checking for Baritone API", t);
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
        if (FabricLoader.getInstance().isModLoaded(BARITONE_MOD_ID)) {
            cachedModResult = Boolean.TRUE;
            return cachedModResult;
        }

        // Relaxed detection for Baritone variants (e.g., meteor-bundled)
        cachedModResult = FabricLoader.getInstance().getAllMods().stream().anyMatch(mod -> {
            String id = mod.getMetadata() != null ? mod.getMetadata().getId() : null;
            if (id != null && id.toLowerCase(java.util.Locale.ROOT).contains("baritone")) {
                return true;
            }
            String name = mod.getMetadata() != null ? mod.getMetadata().getName() : null;
            return name != null && name.toLowerCase(java.util.Locale.ROOT).contains("baritone");
        });
        return cachedModResult;
    }

    /**
     * Marks the dependency as unavailable so future checks skip loading attempts.
     */
    public static void markUnavailable() {
        cachedApiResult = Boolean.FALSE;
    }
}
