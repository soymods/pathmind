package com.pathmind.util;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.util.ActionResult;

import java.util.function.Consumer;

public final class UseItemCallbackCompat {
    private UseItemCallbackCompat() {
    }

    public static void register(Consumer<String> eventSink, String eventName) {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            eventSink.accept(eventName);
            return ActionResult.PASS;
        });
    }
}
