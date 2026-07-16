package com.pathmind.util;

import com.mojang.blaze3d.platform.NativeImage;
import java.lang.reflect.Constructor;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Bridges NativeImageBackedTexture constructor differences across 1.21.x targets.
 */
public final class TextureCompatibilityBridge {
    private TextureCompatibilityBridge() {
    }

    public static DynamicTexture createNativeImageBackedTexture(String debugName, NativeImage image) {
        if (image == null) {
            return null;
        }

        try {
            Constructor<DynamicTexture> supplierCtor = DynamicTexture.class
                .getConstructor(Supplier.class, NativeImage.class);
            Supplier<String> supplier = () -> debugName == null || debugName.isBlank() ? "pathmind_texture" : debugName;
            return supplierCtor.newInstance(supplier, image);
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Constructor<DynamicTexture> imageCtor = DynamicTexture.class
                .getConstructor(NativeImage.class);
            return imageCtor.newInstance(image);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported NativeImageBackedTexture constructor set.", e);
        }
    }
}
