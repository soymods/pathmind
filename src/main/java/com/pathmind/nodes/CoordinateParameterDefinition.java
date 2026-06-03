package com.pathmind.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class CoordinateParameterDefinition {
    static NodeBehaviorDefinition create() {
        return NodeBehaviorDefinition.builder(NodeType.PARAM_COORDINATE)
            .runtimeBehavior(CoordinateParameterDefinition::resolvePositionTarget)
            .comparableBehavior(NodeBehaviorDefinitionSupport.stringComparable((owner, node) -> {
                String formatted = owner.formatCoordinateValues(node.exportParameterValues());
                return formatted.isEmpty() ? Optional.empty() : Optional.of(formatted);
            }))
            .build();
    }

    static BlockPos resolveBlockPosition(Node parameterNode) {
        return resolveBlockPosition(null, parameterNode);
    }

    private static Optional<Vec3d> resolvePositionTarget(Node owner, Node parameterNode, RuntimeParameterData data,
                                                         CompletableFuture<Void> future) {
        BlockPos pos = resolveBlockPosition(owner, parameterNode);
        if (data != null) {
            data.targetBlockPos = pos;
        }
        return Optional.of(Vec3d.ofCenter(pos));
    }

    private static BlockPos resolveBlockPosition(Node owner, Node parameterNode) {
        List<Integer> xs = resolveCoordinateCandidates(owner, parameterNode, "X");
        List<Integer> ys = resolveCoordinateCandidates(owner, parameterNode, "Y");
        List<Integer> zs = resolveCoordinateCandidates(owner, parameterNode, "Z");
        int candidateCount = Math.max(xs.size(), Math.max(ys.size(), zs.size()));
        if (candidateCount <= 1) {
            return new BlockPos(xs.get(0), ys.get(0), zs.get(0));
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return new BlockPos(xs.get(0), ys.get(0), zs.get(0));
        }

        BlockPos playerPos = client.player.getBlockPos();
        BlockPos bestPos = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (int i = 0; i < candidateCount; i++) {
            BlockPos candidate = new BlockPos(valueAt(xs, i), valueAt(ys, i), valueAt(zs, i));
            double distanceSq = playerPos.getSquaredDistance(candidate);
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                bestPos = candidate;
            }
        }
        return bestPos != null ? bestPos : new BlockPos(xs.get(0), ys.get(0), zs.get(0));
    }

    private static List<Integer> resolveCoordinateCandidates(Node owner, Node parameterNode, String parameterName) {
        List<Integer> values = new ArrayList<>();
        String rawValue = Node.getParameterString(parameterNode, parameterName);
        List<String> entries = owner != null ? owner.splitMultiValueList(rawValue) : List.of();
        if (entries.isEmpty()) {
            values.add(Node.parseNodeInt(parameterNode, parameterName, 0));
            return values;
        }

        for (String entry : entries) {
            Integer parsed = resolveCoordinateEntry(parameterNode, parameterName, entry);
            if (parsed != null) {
                values.add(parsed);
                continue;
            }
            parsed = Node.parseIntOrNull(entry);
            if (parsed == null) {
                Double asDouble = Node.parseDoubleOrNull(entry);
                parsed = asDouble != null ? (int) Math.round(asDouble) : 0;
            }
            values.add(parsed);
        }
        if (values.isEmpty()) {
            values.add(0);
        }
        return values;
    }

    private static Integer resolveCoordinateEntry(Node parameterNode, String parameterName, String entry) {
        if (!RelativeInputSupport.supportsRelativeCoordinate(parameterNode, parameterName)) {
            return null;
        }
        Double resolved = RelativeInputSupport.resolveRelativeExpression(entry, currentAxisValue(parameterName));
        return resolved != null ? (int) Math.round(resolved) : null;
    }

    private static int currentAxisValue(String parameterName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return 0;
        }
        BlockPos playerPos = client.player.getBlockPos();
        if ("X".equalsIgnoreCase(parameterName)) {
            return playerPos.getX();
        }
        if ("Y".equalsIgnoreCase(parameterName)) {
            return playerPos.getY();
        }
        if ("Z".equalsIgnoreCase(parameterName)) {
            return playerPos.getZ();
        }
        return 0;
    }

    private static int valueAt(List<Integer> values, int index) {
        if (values.isEmpty()) {
            return 0;
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        return values.get(Math.min(index, values.size() - 1));
    }

    private CoordinateParameterDefinition() {
    }
}
