package com.pathmind.util;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResultHolder;
import java.util.function.Consumer;

public final class UseItemCallbackCompat {
    private UseItemCallbackCompat() {
    }

    public static void register(Consumer<String> eventSink, String eventName) {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            eventSink.accept(eventName);
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        });
    }
}
