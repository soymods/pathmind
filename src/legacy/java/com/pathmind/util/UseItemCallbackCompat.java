package com.pathmind.util;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class UseItemCallbackCompat {
    private UseItemCallbackCompat() {
    }

    public static void register(Consumer<String> eventSink, String eventName) {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            eventSink.accept(eventName);
            return createPassResult(player.getStackInHand(hand));
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T createPassResult(ItemStack stack) {
        try {
            Class<?> typedActionResultClass = Class.forName("net.minecraft.util.TypedActionResult");
            Method passMethod = typedActionResultClass.getMethod("pass", Object.class);
            return (T) passMethod.invoke(null, stack);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return (T) ActionResult.PASS;
        }
    }
}
