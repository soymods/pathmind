package com.pathmind.ui.overlay;

import com.pathmind.execution.PathmindNavigator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class NavigatorWorldOverlay {
    private static final int PATH_COLOR = 0xFF66D8FF;
    private static final int CANDIDATE_PATH_COLOR = 0x6687AFC2;
    private static final int GOAL_COLOR = 0xFFFFA52B;
    private static final double DASH_LENGTH = 0.42D;
    private static final double DASH_GAP = 0.24D;
    private static final double DASH_CYCLE = DASH_LENGTH + DASH_GAP;
    private static final double ANIMATION_SPEED = 1.45D;
    private static final double PATH_THICKNESS = 0.065D;
    private static final double CANDIDATE_THICKNESS = 0.045D;

    private NavigatorWorldOverlay() {
    }

    public static void render(Matrix4f positionMatrix) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client != null ? client.player : null;
        if (client == null || player == null || positionMatrix == null) {
            return;
        }

        PathmindNavigator.Snapshot snapshot = PathmindNavigator.getInstance().getSnapshot();
        if (snapshot == null || !snapshot.active()) {
            return;
        }

        BlockPos goalPos = snapshot.resolvedGoalPos() != null ? snapshot.resolvedGoalPos() : snapshot.targetPos();
        if (goalPos == null) {
            return;
        }

        BufferBuilderStorage bufferBuilders = client.getBufferBuilders();
        if (bufferBuilders == null) {
            return;
        }

        VertexConsumerProvider.Immediate consumers = bufferBuilders.getEffectVertexConsumers();
        if (consumers == null) {
            return;
        }

        MatrixStack matrices = new MatrixStack();
        matrices.loadIdentity();
        matrices.multiplyPositionMatrix(positionMatrix);

        try {
            renderCandidatePaths(matrices, consumers, player, snapshot.candidatePaths());
            if (snapshot.path().isEmpty()) {
                renderFallbackPath(matrices, consumers, player, goalPos);
            } else {
                renderPath(matrices, consumers, player, snapshot.path());
            }
            renderGoal(matrices, consumers, goalPos);
        } catch (Throwable ignored) {
            // Never fail the world renderer because of overlay drawing.
        } finally {
            consumers.draw();
        }
    }

    private static void renderCandidatePaths(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        ClientPlayerEntity player,
        List<List<BlockPos>> candidatePaths
    ) {
        if (candidatePaths == null || candidatePaths.size() <= 1) {
            return;
        }

        double phase = animationPhase();
        for (int i = 1; i < candidatePaths.size(); i++) {
            List<BlockPos> candidate = candidatePaths.get(i);
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }

            List<Vec3d> points = new ArrayList<>(candidate.size() + 1);
            points.add(new Vec3d(player.getX(), player.getY() + 0.18D, player.getZ()));
            for (BlockPos node : candidate) {
                points.add(pathPoint(node));
            }
            renderDashedSegments(matrices, consumers, points, phase, CANDIDATE_PATH_COLOR, CANDIDATE_THICKNESS);
        }
    }

    private static void renderPath(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        ClientPlayerEntity player,
        List<BlockPos> path
    ) {
        if (path == null || path.isEmpty()) {
            return;
        }

        List<Vec3d> points = new ArrayList<>(path.size() + 1);
        points.add(new Vec3d(player.getX(), player.getY() + 0.18D, player.getZ()));
        for (BlockPos node : path) {
            points.add(pathPoint(node));
        }
        renderDashedSegments(matrices, consumers, points, animationPhase(), PATH_COLOR, PATH_THICKNESS);
    }

    private static void renderFallbackPath(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        ClientPlayerEntity player,
        BlockPos goalPos
    ) {
        List<Vec3d> points = List.of(
            new Vec3d(player.getX(), player.getY() + 0.18D, player.getZ()),
            pathPoint(goalPos)
        );
        renderDashedSegments(matrices, consumers, points, animationPhase(), PATH_COLOR, PATH_THICKNESS);
    }

    private static void renderDashedSegments(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        List<Vec3d> points,
        double phase,
        int color,
        double thickness
    ) {
        if (points.size() < 2) {
            return;
        }

        for (int i = 1; i < points.size(); i++) {
            renderAnimatedSegment(matrices, consumers, points.get(i - 1), points.get(i), phase, color, thickness);
        }
    }

    private static void renderAnimatedSegment(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        Vec3d start,
        Vec3d end,
        double phase,
        int color,
        double thickness
    ) {
        double segmentLength = start.distanceTo(end);
        if (segmentLength <= 0.0001D) {
            return;
        }

        Vec3d direction = end.subtract(start).normalize();
        double offset = -phase;
        while (offset < segmentLength) {
            double dashStartDistance = Math.max(0.0D, offset);
            double dashEndDistance = Math.min(segmentLength, offset + DASH_LENGTH);
            if (dashEndDistance > dashStartDistance) {
                Vec3d dashStart = start.add(direction.multiply(dashStartDistance));
                Vec3d dashEnd = start.add(direction.multiply(dashEndDistance));
                renderFilledPrism(matrices, consumers, dashBox(dashStart, dashEnd, thickness), color);
            }
            offset += DASH_CYCLE;
        }
    }

    private static void renderGoal(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        BlockPos goalPos
    ) {
        Box goalMarker = new Box(
            goalPos.getX(),
            goalPos.getY(),
            goalPos.getZ(),
            goalPos.getX() + 1.0D,
            goalPos.getY() + 2.0D,
            goalPos.getZ() + 1.0D
        );
        renderFilledPrism(matrices, consumers, goalMarker, withAlpha(GOAL_COLOR, 0.26F));
        DebugRenderer.drawBox(matrices, consumers, goalMarker, red(GOAL_COLOR), green(GOAL_COLOR), blue(GOAL_COLOR), 0.95F);
    }

    private static void renderFilledPrism(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        Box box,
        int color
    ) {
        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getDebugFilledBox());
        VertexRendering.drawFilledBox(
            matrices,
            buffer,
            box.minX,
            box.minY,
            box.minZ,
            box.maxX,
            box.maxY,
            box.maxZ,
            red(color),
            green(color),
            blue(color),
            alpha(color)
        );
    }

    private static Box dashBox(Vec3d start, Vec3d end, double thickness) {
        double minX = Math.min(start.x, end.x) - thickness;
        double minY = Math.min(start.y, end.y) - thickness;
        double minZ = Math.min(start.z, end.z) - thickness;
        double maxX = Math.max(start.x, end.x) + thickness;
        double maxY = Math.max(start.y, end.y) + thickness;
        double maxZ = Math.max(start.z, end.z) + thickness;
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static double animationPhase() {
        return (System.currentTimeMillis() / 1000.0D * ANIMATION_SPEED) % DASH_CYCLE;
    }

    private static Vec3d pathPoint(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.18D, pos.getZ() + 0.5D);
    }

    private static int withAlpha(int color, float alpha) {
        int alphaByte = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (alphaByte << 24) | (color & 0x00FFFFFF);
    }

    private static float red(int color) {
        return ((color >> 16) & 0xFF) / 255.0F;
    }

    private static float green(int color) {
        return ((color >> 8) & 0xFF) / 255.0F;
    }

    private static float blue(int color) {
        return (color & 0xFF) / 255.0F;
    }

    private static float alpha(int color) {
        return ((color >>> 24) & 0xFF) / 255.0F;
    }
}
