package com.pathmind.nodes;

import java.util.concurrent.CompletableFuture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

interface NodeGotoFallbackTargetBehavior {
    BlockPos resolveFallbackTarget(Node owner, Node parameterNode, MinecraftClient client, CompletableFuture<Void> future);
}
