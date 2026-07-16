package com.pathmind.mixin;

import com.pathmind.util.LoaderMetadata;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies optional compatibility mixins only when their target classes exist.
 */
public final class PathmindMixinPlugin implements IMixinConfigPlugin {
    private static final String UI_UTILS_OVERLAY_MIXIN = "com.pathmind.mixin.UiUtilsOverlayBlockerMixin";
    private static final String UI_UTILS_OVERLAY_TARGET = "com.mrbreaknfix.ui_utils.gui.BaseOverlay";
    // Legacy Fabric catch-all that injects at a Screen tooltip-render invocation inside
    // GameRenderer.render. That descriptor is unavailable to the NeoForge production transform,
    // and NeoForge already draws Pathmind HUD/overlays
    // through RenderGuiEvent.Post, so skip this mixin entirely on NeoForge.
    private static final String GAME_RENDERER_MIXIN = "com.pathmind.mixin.GameRendererMixin";
    // Fabric needs a mixin at the end of vanilla HUD extraction. NeoForge exposes
    // the equivalent typed RenderGuiEvent.Post hook and must not render it twice.
    private static final String MC26_HUD_OVERLAY_MIXIN = "com.pathmind.mixin.Mc26HudOverlayMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (UI_UTILS_OVERLAY_MIXIN.equals(mixinClassName)) {
            return classExists(UI_UTILS_OVERLAY_TARGET);
        }
        if (GAME_RENDERER_MIXIN.equals(mixinClassName) && LoaderMetadata.isNeoForge()) {
            return false;
        }
        if (MC26_HUD_OVERLAY_MIXIN.equals(mixinClassName) && LoaderMetadata.isNeoForge()) {
            return false;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private boolean classExists(String className) {
        try {
            Class.forName(className, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
