package com.pathmind.mixin;

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
