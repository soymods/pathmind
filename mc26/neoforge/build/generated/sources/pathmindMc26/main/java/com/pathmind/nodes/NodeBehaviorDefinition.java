package com.pathmind.nodes;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class NodeBehaviorDefinition {
    private final NodeType type;
    private final NodeParameterBehavior parameterBehavior;
    private final NodeParameterRuntimeBehavior runtimeBehavior;
    private final NodeParameterListEntryBehavior listEntryBehavior;
    private final NodeGotoFallbackTargetBehavior gotoFallbackTargetBehavior;
    private final NodeComparableBehavior comparableBehavior;

    private NodeBehaviorDefinition(Builder builder) {
        this.type = builder.type;
        this.parameterBehavior = builder.parameterBehavior;
        this.runtimeBehavior = builder.runtimeBehavior;
        this.listEntryBehavior = builder.listEntryBehavior;
        this.gotoFallbackTargetBehavior = builder.gotoFallbackTargetBehavior;
        this.comparableBehavior = builder.comparableBehavior;
    }

    NodeType getType() {
        return type;
    }

    boolean hasAnyBehavior() {
        return parameterBehavior != null
            || runtimeBehavior != null
            || listEntryBehavior != null
            || gotoFallbackTargetBehavior != null
            || comparableBehavior != null;
    }

    boolean hasParameterBehavior() {
        return parameterBehavior != null;
    }

    boolean hasRuntimeBehavior() {
        return runtimeBehavior != null;
    }

    boolean hasListEntryBehavior() {
        return listEntryBehavior != null;
    }

    boolean hasGotoFallbackTargetBehavior() {
        return gotoFallbackTargetBehavior != null;
    }

    boolean hasComparableBehavior() {
        return comparableBehavior != null;
    }

    Map<String, String> exportValues(Node node, Map<String, String> baseValues) {
        return parameterBehavior != null ? parameterBehavior.exportValues(node, baseValues) : baseValues;
    }

    Optional<Vec3> resolvePositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                          CompletableFuture<Void> future) {
        return runtimeBehavior != null
            ? runtimeBehavior.resolvePositionTarget(owner, parameterNode, data, future)
            : Optional.empty();
    }

    Node.ListValueEntry resolveListValueEntry(Node owner, Node parameterNode, Minecraft client) {
        return listEntryBehavior != null ? listEntryBehavior.resolveListValueEntry(owner, parameterNode, client) : null;
    }

    BlockPos resolveGotoFallbackTarget(Node owner, Node parameterNode, Minecraft client,
                                       CompletableFuture<Void> future) {
        return gotoFallbackTargetBehavior != null
            ? gotoFallbackTargetBehavior.resolveFallbackTarget(owner, parameterNode, client, future)
            : null;
    }

    Optional<String> resolveComparableString(Node owner, Node node) {
        return comparableBehavior != null ? comparableBehavior.resolveString(owner, node) : Optional.empty();
    }

    Optional<Double> resolveComparableNumber(Node owner, Node node) {
        return comparableBehavior != null ? comparableBehavior.resolveNumber(owner, node) : Optional.empty();
    }

    static Builder builder(NodeType type) {
        return new Builder(type);
    }

    static final class Builder {
        private final NodeType type;
        private NodeParameterBehavior parameterBehavior;
        private NodeParameterRuntimeBehavior runtimeBehavior;
        private NodeParameterListEntryBehavior listEntryBehavior;
        private NodeGotoFallbackTargetBehavior gotoFallbackTargetBehavior;
        private NodeComparableBehavior comparableBehavior;

        private Builder(NodeType type) {
            this.type = type;
        }

        Builder parameterBehavior(NodeParameterBehavior behavior) {
            this.parameterBehavior = behavior;
            return this;
        }

        Builder runtimeBehavior(NodeParameterRuntimeBehavior behavior) {
            this.runtimeBehavior = behavior;
            return this;
        }

        Builder listEntryBehavior(NodeParameterListEntryBehavior behavior) {
            this.listEntryBehavior = behavior;
            return this;
        }

        Builder gotoFallbackTargetBehavior(NodeGotoFallbackTargetBehavior behavior) {
            this.gotoFallbackTargetBehavior = behavior;
            return this;
        }

        Builder comparableBehavior(NodeComparableBehavior behavior) {
            this.comparableBehavior = behavior;
            return this;
        }

        NodeBehaviorDefinition build() {
            return new NodeBehaviorDefinition(this);
        }
    }
}
