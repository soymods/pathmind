package com.pathmind.execution;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

final class NavigatorPlacementPolicy {
    static final long CONFIRM_WINDOW_MS = 750L;
    static final int MAX_ATTEMPTS = 3;

    enum Verification {
        PROCEED,
        CONFIRMED,
        WAITING,
        EXHAUSTED
    }

    private NavigatorPlacementPolicy() {
    }

    static Verification verify(
        BlockPos pendingTarget,
        BlockPos requestedTarget,
        boolean placed,
        int attempts,
        long now,
        long pendingUntilMs
    ) {
        if (pendingTarget == null || requestedTarget == null || !pendingTarget.equals(requestedTarget)) {
            return Verification.PROCEED;
        }
        if (placed) {
            return Verification.CONFIRMED;
        }
        if (now < pendingUntilMs) {
            return Verification.WAITING;
        }
        return attempts >= MAX_ATTEMPTS ? Verification.EXHAUSTED : Verification.PROCEED;
    }

    static int nextAttemptCount(int attempts) {
        return Math.max(0, attempts) + 1;
    }

    /** Decorative BlockItems (for example flowers) cannot support a pillar. */
    static boolean isSolidSupportBlock(ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && stack.getItem() instanceof BlockItem blockItem
            && blockItem.getBlock().defaultBlockState().canOcclude();
    }
}
