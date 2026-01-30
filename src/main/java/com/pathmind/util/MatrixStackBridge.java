package com.pathmind.util;

import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3x2fStack;

/**
 * Provides compatibility shims for DrawContext#getMatrices() return types across 1.21.x releases.
 */
public final class MatrixStackBridge {
    private MatrixStackBridge() {
    }

    public static void push(Object matrices) {
        if (matrices instanceof MatrixStack stack) {
            stack.push();
        } else if (matrices instanceof Matrix3x2fStack stack3x2) {
            stack3x2.pushMatrix();
        } else {
            throw unsupportedStack(matrices);
        }
    }

    public static void pop(Object matrices) {
        if (matrices instanceof MatrixStack stack) {
            stack.pop();
        } else if (matrices instanceof Matrix3x2fStack stack3x2) {
            stack3x2.popMatrix();
        } else {
            throw unsupportedStack(matrices);
        }
    }

    public static void scale(Object matrices, float x, float y) {
        if (matrices instanceof MatrixStack stack) {
            stack.scale(x, y, 1.0f);
        } else if (matrices instanceof Matrix3x2fStack stack3x2) {
            stack3x2.scale(x, y);
        } else {
            throw unsupportedStack(matrices);
        }
    }

    /**
     * Translates in 2D space for both legacy and modern GUI stacks.
     */
    public static void translate(Object matrices, float x, float y) {
        if (matrices instanceof MatrixStack stack) {
            stack.translate(x, y, 0.0f);
        } else if (matrices instanceof Matrix3x2fStack stack3x2) {
            stack3x2.translate(x, y);
        } else {
            throw unsupportedStack(matrices);
        }
    }

    /**
     * Translates along the Z axis when available so elements can render above other GUI layers.
     * Matrix3x2f stacks do not support depth, so this becomes a no-op on those versions.
     */
    public static void translateZ(Object matrices, float z) {
        if (z == 0.0f) {
            return;
        }
        if (matrices instanceof MatrixStack stack) {
            stack.translate(0.0f, 0.0f, z);
        } else if (matrices instanceof Matrix3x2fStack) {
            // Matrix3x2f is strictly 2D and ignores depth translations.
        } else {
            throw unsupportedStack(matrices);
        }
    }

    public static boolean isMatrixStack(Object matrices) {
        return matrices instanceof MatrixStack;
    }

    public static boolean isModernGuiStack(Object matrices) {
        return matrices instanceof Matrix3x2fStack;
    }

    private static IllegalStateException unsupportedStack(Object matrices) {
        String type = matrices == null ? "null" : matrices.getClass().getName();
        return new IllegalStateException("Unsupported matrix stack type: " + type);
    }
}
