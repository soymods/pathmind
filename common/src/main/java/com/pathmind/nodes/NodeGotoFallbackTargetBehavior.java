package com.pathmind.nodes;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

interface NodeGotoFallbackTargetBehavior {
    BlockPos resolveFallbackTarget(Node owner, Node parameterNode, Minecraft client, CompletableFuture<Void> future);
}
