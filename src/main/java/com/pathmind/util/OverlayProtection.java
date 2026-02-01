package com.pathmind.util;

import com.pathmind.screen.PathmindScreens;
import net.minecraft.client.MinecraftClient;

/**
 * Utility class to prevent other mods' overlays from rendering when Pathmind GUI is open.
 * This provides multi-layered protection against intrusive overlay mods.
 */
public class OverlayProtection {

    private static boolean registered = false;
    private static final ThreadLocal<Boolean> isPathmindRendering = ThreadLocal.withInitial(() -> false);
    private static final String[] CORE_PACKAGE_PREFIXES = {
        "net.minecraft.",
        "com.mojang.",
        "com.mojang.blaze3d.",
        "net.fabricmc.",
        "org.lwjgl.",
        "org.joml.",
        "org.spongepowered.",
        "it.unimi.",
        "com.google.",
        "org.slf4j.",
        "java.",
        "javax.",
        "sun.",
        "jdk."
    };

    /**
     * Registers overlay protection.
     * Should be called during mod initialization.
     *
     * Note: The actual protection is implemented via multiple mixins:
     * - InGameHudMixin: Blocks HUD rendering during gameplay
     * - DrawContextMixin: Blocks draw calls from other mods during screen rendering
     * - GameRendererMixin: Provides injection points for additional blocking
     */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        // The main protection is handled by various mixins that intercept
        // rendering calls at different points in the pipeline.
    }

    /**
     * Marks that Pathmind is currently rendering.
     * This prevents the DrawContextMixin from blocking Pathmind's own draw calls.
     *
     * @param rendering true if Pathmind is rendering, false otherwise
     */
    public static void setPathmindRendering(boolean rendering) {
        isPathmindRendering.set(rendering);
    }

    /**
     * Checks if Pathmind is currently rendering.
     * @return true if Pathmind is actively rendering
     */
    public static boolean isPathmindRendering() {
        return isPathmindRendering.get();
    }

    /**
     * Checks if overlay protection should be active.
     * @return true if the Pathmind GUI is currently open
     */
    public static boolean isProtectionActive() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.currentScreen != null
            && PathmindScreens.isVisualEditorScreen(client.currentScreen);
    }

    /**
     * Determines whether a draw call should be blocked based on the call stack.
     * Allows only vanilla/Minecraft/Pathmind rendering paths while the editor is open.
     */
    public static boolean shouldBlockExternalDraw() {
        if (!isProtectionActive()) {
            return false;
        }
        if (isPathmindRendering()) {
            return false;
        }

        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (isCorePackage(className)) {
                continue;
            }
            if (isPathmindInternalFrame(className)) {
                continue;
            }

            // First non-core caller decides whether this draw is allowed.
            return !className.startsWith("com.pathmind.");
        }

        return false;
    }

    private static boolean isCorePackage(String className) {
        for (String prefix : CORE_PACKAGE_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPathmindInternalFrame(String className) {
        return className.startsWith("com.pathmind.mixin.")
            || className.equals("com.pathmind.util.OverlayProtection");
    }

    public static boolean isPathmindInternalCallStack() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (isCorePackage(className)) {
                continue;
            }
            return className.startsWith("com.pathmind.");
        }
        return false;
    }

}
