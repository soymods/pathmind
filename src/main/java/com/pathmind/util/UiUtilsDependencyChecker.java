package com.pathmind.util;

import com.pathmind.PathmindMod;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Lightweight runtime check for UI Utils availability.
 * We only rely on the mod being loaded and a core class resolving.
 */
public final class UiUtilsDependencyChecker {
    private static final String LEGACY_MOD_ID = "ui-utils";
    private static final String MODERN_MOD_ID = "ui_utils";
    private static final String LEGACY_SHARED_VARIABLES_CLASS = "com.ui_utils.SharedVariables";
    private static final String MODERN_CORE_CLASS = "com.mrbreaknfix.ui_utils.command.CommandSystem";
    private static Boolean cachedResult;

    private UiUtilsDependencyChecker() {
    }

    /**
     * @return true if the UI Utils mod is loaded and core classes resolve.
     */
    public static boolean isUiUtilsPresent() {
        if (cachedResult != null) {
            return cachedResult;
        }

        boolean legacyLoaded = FabricLoader.getInstance().isModLoaded(LEGACY_MOD_ID);
        boolean modernLoaded = FabricLoader.getInstance().isModLoaded(MODERN_MOD_ID);
        if (!legacyLoaded && !modernLoaded) {
            cachedResult = Boolean.FALSE;
            return false;
        }

        try {
            if (legacyLoaded) {
                Class.forName(LEGACY_SHARED_VARIABLES_CLASS, false, UiUtilsDependencyChecker.class.getClassLoader());
                cachedResult = Boolean.TRUE;
                return true;
            }
            Class.forName(MODERN_CORE_CLASS, false, UiUtilsDependencyChecker.class.getClassLoader());
            cachedResult = Boolean.TRUE;
        } catch (ClassNotFoundException e) {
            PathmindMod.LOGGER.info("UI Utils detected but incompatible; integration disabled.");
            cachedResult = Boolean.FALSE;
        } catch (LinkageError e) {
            PathmindMod.LOGGER.info("UI Utils failed to initialize; integration disabled.");
            cachedResult = Boolean.FALSE;
        } catch (Throwable t) {
            PathmindMod.LOGGER.info("UI Utils check failed unexpectedly; integration disabled.");
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
