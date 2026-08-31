package com.pathmind.nodes;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;

/**
 * Single owner of the forward key for Walk nodes.
 *
 * <p>Walk nodes used to write {@code keyUp} directly, so a one-second timed walk finishing
 * released the key out from under any walk still running in another chain. Holds are counted
 * instead: the key stays down while anyone still wants it.
 *
 * <p>ponytail: this arbitrates Walk against Walk only. {@code NavigatorPrimitiveExecutor}
 * drives {@code keyUp} as a per-tick servo loop and will still overwrite a hold while
 * pathfinding is active; making the navigator a hold participant is a separate change.
 */
public final class WalkHold {
    private static final AtomicInteger HOLDS = new AtomicInteger();
    /** The open-ended hold owned by Start Walking, released by Stop Walking. */
    private static final AtomicBoolean SUSTAINED = new AtomicBoolean();

    private WalkHold() {
    }

    public static void acquire(Minecraft client) {
        HOLDS.incrementAndGet();
        apply(client, true);
    }

    public static void release(Minecraft client) {
        if (HOLDS.updateAndGet(current -> current <= 1 ? 0 : current - 1) == 0) {
            apply(client, false);
        }
    }

    public static void startSustained(Minecraft client) {
        if (SUSTAINED.compareAndSet(false, true)) {
            acquire(client);
        }
    }

    public static void stopSustained(Minecraft client) {
        if (SUSTAINED.compareAndSet(true, false)) {
            release(client);
        }
    }

    /** Stop-all must not leave the player walking into a ravine forever. */
    public static void releaseAll(Minecraft client) {
        SUSTAINED.set(false);
        HOLDS.set(0);
        apply(client, false);
    }

    static boolean isHeld() {
        return HOLDS.get() > 0;
    }

    private static void apply(Minecraft client, boolean down) {
        if (client == null) {
            return;
        }
        client.execute(() -> {
            if (client.options != null && client.options.keyUp != null) {
                client.options.keyUp.setDown(down);
            }
        });
    }
}
