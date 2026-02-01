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
        return client.currentScreen != null && PathmindScreens.isVisualEditorScreen(client.currentScreen);
    }
}
