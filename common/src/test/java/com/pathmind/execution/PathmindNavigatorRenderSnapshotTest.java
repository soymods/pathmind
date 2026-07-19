package com.pathmind.execution;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathmindNavigatorRenderSnapshotTest {
    @Test
    void renderSnapshotIsNotBuiltOnDemandByRenderConsumers() {
        PathmindNavigator navigator = PathmindNavigator.getInstance();
        navigator.reset();
        try {
            assertTrue(navigator.startGoto(new BlockPos(8, 64, 8), "test", new CompletableFuture<>()));

            assertNull(navigator.getSnapshot());
        } finally {
            navigator.reset();
        }
    }
}
