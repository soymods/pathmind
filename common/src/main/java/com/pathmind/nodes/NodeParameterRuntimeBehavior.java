package com.pathmind.nodes;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.phys.Vec3;

interface NodeParameterRuntimeBehavior {
    Optional<Vec3> resolvePositionTarget(Node owner, Node parameterNode, RuntimeParameterData data, CompletableFuture<Void> future);
}
