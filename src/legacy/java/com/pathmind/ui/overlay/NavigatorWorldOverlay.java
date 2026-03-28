package com.pathmind.ui.overlay;

import com.pathmind.execution.PathmindNavigator;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

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
    private static final double GOAL_HEIGHT = 2.0D;

    private NavigatorWorldOverlay() {
    }

    public static void render(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        double cameraX,
        double cameraY,
        double cameraZ
    ) {
        if (matrices == null || consumers == null) {
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

        matrices.push();
        matrices.translate(-cameraX, -cameraY, -cameraZ);
        try {
            renderCandidatePaths(matrices, consumers, snapshot.candidatePaths());
            if (snapshot.path().isEmpty()) {
                renderFallbackPath(matrices, consumers, cameraX, cameraY, cameraZ, goalPos);
            } else {
                renderPath(matrices, consumers, cameraX, cameraY, cameraZ, snapshot.path());
            }
            renderGoal(matrices, consumers, goalPos);
        } catch (Throwable ignored) {
            // Never fail the debug renderer because of Pathmind indicators.
        } finally {
            matrices.pop();
        }
    }

    private static void renderCandidatePaths(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
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
            renderDashedSegments(matrices, consumers, candidatePathPoints(candidate), phase, CANDIDATE_PATH_COLOR);
        }
    }

    private static void renderPath(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        double cameraX,
        double cameraY,
        double cameraZ,
        List<BlockPos> path
    ) {
        if (path == null || path.isEmpty()) {
            return;
        }

        List<Vec3d> points = new ArrayList<>(path.size() + 1);
        points.add(new Vec3d(cameraX, cameraY + 0.18D, cameraZ));
        for (BlockPos node : path) {
            points.add(pathPoint(node));
        }
        renderDashedSegments(matrices, consumers, points, animationPhase(), PATH_COLOR);
    }

    private static void renderFallbackPath(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        double cameraX,
        double cameraY,
        double cameraZ,
        BlockPos goalPos
    ) {
        List<Vec3d> points = List.of(
            new Vec3d(cameraX, cameraY + 0.18D, cameraZ),
            pathPoint(goalPos)
        );
        renderDashedSegments(matrices, consumers, points, animationPhase(), PATH_COLOR);
    }

    private static List<Vec3d> candidatePathPoints(List<BlockPos> candidate) {
        List<Vec3d> points = new ArrayList<>(candidate.size());
        for (BlockPos node : candidate) {
            points.add(pathPoint(node));
        }
        return points;
    }

    private static void renderDashedSegments(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        List<Vec3d> points,
        double phase,
        int color
    ) {
        if (points.size() < 2) {
            return;
        }

        for (int i = 1; i < points.size(); i++) {
            renderAnimatedSegment(matrices, consumers, points.get(i - 1), points.get(i), phase, color);
        }
    }

    private static void renderAnimatedSegment(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        Vec3d start,
        Vec3d end,
        double phase,
        int color
    ) {
        double segmentLength = start.distanceTo(end);
        if (segmentLength <= 0.0001D) {
            return;
        }

        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        Vec3d direction = end.subtract(start).normalize();
        double offset = -phase;
        while (offset < segmentLength) {
            double dashStartDistance = Math.max(0.0D, offset);
            double dashEndDistance = Math.min(segmentLength, offset + DASH_LENGTH);
            if (dashEndDistance > dashStartDistance) {
                Vec3d dashStart = start.add(direction.multiply(dashStartDistance));
                Vec3d dashEnd = start.add(direction.multiply(dashEndDistance));
                drawLine(matrices, lines, dashStart, dashEnd, color);
            }
            offset += DASH_CYCLE;
        }
    }

    private static void renderGoal(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        BlockPos goalPos
    ) {
        double minX = goalPos.getX();
        double minY = goalPos.getY();
        double minZ = goalPos.getZ();
        double maxX = minX + 1.0D;
        double maxY = minY + GOAL_HEIGHT;
        double maxZ = minZ + 1.0D;
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

        renderGoalEdge(matrices, lines, minX, minY, minZ, minX, maxY, minZ);
        renderGoalEdge(matrices, lines, maxX, minY, minZ, maxX, maxY, minZ);
        renderGoalEdge(matrices, lines, minX, minY, maxZ, minX, maxY, maxZ);
        renderGoalEdge(matrices, lines, maxX, minY, maxZ, maxX, maxY, maxZ);

        renderGoalEdge(matrices, lines, minX, minY, minZ, maxX, minY, minZ);
        renderGoalEdge(matrices, lines, minX, minY, maxZ, maxX, minY, maxZ);
        renderGoalEdge(matrices, lines, minX, maxY, minZ, maxX, maxY, minZ);
        renderGoalEdge(matrices, lines, minX, maxY, maxZ, maxX, maxY, maxZ);

        renderGoalEdge(matrices, lines, minX, minY, minZ, minX, minY, maxZ);
        renderGoalEdge(matrices, lines, maxX, minY, minZ, maxX, minY, maxZ);
        renderGoalEdge(matrices, lines, minX, maxY, minZ, minX, maxY, maxZ);
        renderGoalEdge(matrices, lines, maxX, maxY, minZ, maxX, maxY, maxZ);
    }

    private static void renderGoalEdge(
        MatrixStack matrices,
        VertexConsumer lines,
        double startX,
        double startY,
        double startZ,
        double endX,
        double endY,
        double endZ
    ) {
        drawLine(matrices, lines, new Vec3d(startX, startY, startZ), new Vec3d(endX, endY, endZ), GOAL_COLOR);
    }

    private static double animationPhase() {
        return (System.currentTimeMillis() / 1000.0D * ANIMATION_SPEED) % DASH_CYCLE;
    }

    private static Vec3d pathPoint(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.18D, pos.getZ() + 0.5D);
    }

    private static void drawLine(MatrixStack matrices, VertexConsumer lines, Vec3d start, Vec3d end, int color) {
        MatrixStack.Entry entry = matrices.peek();
        Vec3d delta = end.subtract(start);
        double length = delta.length();
        float nx = 0.0F;
        float ny = 0.0F;
        float nz = 0.0F;
        if (length > 0.0001D) {
            nx = (float) (delta.x / length);
            ny = (float) (delta.y / length);
            nz = (float) (delta.z / length);
        }

        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        int alpha = (color >>> 24) & 0xFF;

        lines.vertex(entry, (float) start.x, (float) start.y, (float) start.z)
            .color(red, green, blue, alpha)
            .normal(entry, nx, ny, nz);
        lines.vertex(entry, (float) end.x, (float) end.y, (float) end.z)
            .color(red, green, blue, alpha)
            .normal(entry, nx, ny, nz);
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
