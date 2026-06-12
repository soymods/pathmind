package com.pathmind.nodes;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import net.minecraft.util.math.Vec3d;

interface NodeParameterRuntimeBehavior {
    Optional<Vec3d> resolvePositionTarget(Node owner, Node parameterNode, RuntimeParameterData data, CompletableFuture<Void> future);
}
