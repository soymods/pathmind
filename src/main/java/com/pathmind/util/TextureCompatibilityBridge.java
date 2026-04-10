package com.pathmind.util;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

import java.lang.reflect.Constructor;
import java.util.function.Supplier;

/**
 * Bridges NativeImageBackedTexture constructor differences across 1.21.x targets.
 */
public final class TextureCompatibilityBridge {
    private TextureCompatibilityBridge() {
    }

    public static NativeImageBackedTexture createNativeImageBackedTexture(String debugName, NativeImage image) {
        if (image == null) {
            return null;
        }

        try {
            Constructor<NativeImageBackedTexture> supplierCtor = NativeImageBackedTexture.class
                .getConstructor(Supplier.class, NativeImage.class);
            Supplier<String> supplier = () -> debugName == null || debugName.isBlank() ? "pathmind_texture" : debugName;
            return supplierCtor.newInstance(supplier, image);
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Constructor<NativeImageBackedTexture> imageCtor = NativeImageBackedTexture.class
                .getConstructor(NativeImage.class);
            return imageCtor.newInstance(image);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported NativeImageBackedTexture constructor set.", e);
        }
    }
}
