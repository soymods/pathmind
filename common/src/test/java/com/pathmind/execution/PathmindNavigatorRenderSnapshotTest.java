package com.pathmind.execution;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void replacingNavigationCancelsThePreviousFuture() {
        PathmindNavigator navigator = PathmindNavigator.getInstance();
        navigator.reset();
        CompletableFuture<Void> first = new CompletableFuture<>();
        CompletableFuture<Void> second = new CompletableFuture<>();
        try {
            assertTrue(navigator.startGoto(new BlockPos(8, 64, 8), "first", first));
            assertTrue(navigator.startGoto(new BlockPos(16, 64, 16), "second", second));

            assertTrue(first.isCompletedExceptionally());
            assertFalse(second.isDone());
        } finally {
            navigator.reset();
        }
    }

    @Test
    void explicitStopCompletesTheActiveFutureNormally() {
        PathmindNavigator navigator = PathmindNavigator.getInstance();
        navigator.reset();
        CompletableFuture<Void> future = new CompletableFuture<>();
        assertTrue(navigator.startGoto(new BlockPos(8, 64, 8), "test", future));

        navigator.stop("test stop");

        assertTrue(future.isDone());
        assertFalse(future.isCompletedExceptionally());
    }
}
