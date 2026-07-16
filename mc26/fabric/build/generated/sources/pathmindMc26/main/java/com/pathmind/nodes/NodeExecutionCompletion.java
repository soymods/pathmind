package com.pathmind.nodes;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

final class NodeExecutionCompletion {
    static void complete(CompletableFuture<Void> future) {
        if (future != null && !future.isDone()) {
            future.complete(null);
        }
    }

    static void fail(Node owner, Minecraft client, CompletableFuture<Void> future, String message) {
        if (owner != null && client != null && message != null && !message.isEmpty()) {
            owner.sendNodeErrorMessage(client, message);
        }
        complete(future);
    }

    static void failWithCurrentClient(Node owner, CompletableFuture<Void> future, String message) {
        fail(owner, Minecraft.getInstance(), future, message);
    }

    static void completeExceptionally(CompletableFuture<Void> future, Throwable throwable) {
        if (future != null && !future.isDone()) {
            future.completeExceptionally(throwable);
        }
    }

    private NodeExecutionCompletion() {
    }
}
