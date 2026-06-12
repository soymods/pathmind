package com.pathmind.ui.overlay;

import com.pathmind.execution.PathmindNavigator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
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
    private static final int STEP_COLOR = 0xFF7FD36B;
    private static final int BREAK_COLOR = 0xFFFF5A4F;
    private static final int PLACE_COLOR = 0xFFC47BFF;
    private static final double DASH_LENGTH = 0.42D;
    private static final double DASH_GAP = 0.24D;
    private static final double DASH_CYCLE = DASH_LENGTH + DASH_GAP;
    private static final double ANIMATION_SPEED = 1.45D;
    private static final double GOAL_HEIGHT = 2.0D;
    private static final double GOAL_INSET = 0.02D;

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
        if (snapshot == null || (snapshot.state() != PathmindNavigator.State.PATHING && snapshot.state() != PathmindNavigator.State.PREVIEW)) {
            return;
        }

        BlockPos goalPos = snapshot.targetPos();
        if (goalPos == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client != null ? client.player : null;
        if (player == null) {
            return;
        }

        Vec3d cameraPos = new Vec3d(cameraX, cameraY, cameraZ);
        try {
            renderCandidatePaths(matrices, consumers, player, snapshot.candidatePaths(), cameraPos);
            renderStepMarkers(matrices, consumers, snapshot.path(), snapshot.visitedPathIndex(), cameraPos);
            renderBreakTargets(matrices, consumers, snapshot.breakTargets(), cameraPos);
            renderPlaceTargets(matrices, consumers, snapshot.placeTargets(), cameraPos);
            renderPath(matrices, consumers, player, snapshot.path(), goalPos, snapshot.visitedPathIndex(), cameraPos);
            renderGoal(matrices, consumers, goalPos, cameraPos);
        } catch (Throwable ignored) {
            // Never fail the debug renderer because of overlay drawing.
        }
    }

    private static void renderCandidatePaths(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        ClientPlayerEntity player,
        List<List<BlockPos>> candidatePaths,
        Vec3d cameraPos
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
            points.add(cameraRelative(new Vec3d(player.getX(), player.getY() + 0.18D, player.getZ()), cameraPos));
            for (BlockPos node : candidate) {
                points.add(cameraRelative(pathPoint(node), cameraPos));
            }
            renderDashedSegments(matrices, consumers, points, phase, CANDIDATE_PATH_COLOR);
        }
    }

    private static void renderPath(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        ClientPlayerEntity player,
        List<BlockPos> path,
        BlockPos goalPos,
        int pathIndex,
        Vec3d cameraPos
    ) {
        List<Vec3d> points = new ArrayList<>((path == null ? 0 : path.size()) + 2);
        points.add(cameraRelative(new Vec3d(player.getX(), player.getY() + 0.18D, player.getZ()), cameraPos));
        if (path != null) {
            int startIndex = Math.max(0, Math.min(pathIndex, path.size()));
            for (int i = startIndex; i < path.size(); i++) {
                BlockPos node = path.get(i);
                points.add(cameraRelative(pathPoint(node), cameraPos));
            }
        }
        Vec3d goalPoint = cameraRelative(pathPoint(goalPos), cameraPos);
        if (points.isEmpty() || !sameRenderPoint(points.get(points.size() - 1), goalPoint)) {
            points.add(goalPoint);
        }
        if (points.size() < 2) {
            return;
        }
        renderDashedSegments(matrices, consumers, points, animationPhase(), PATH_COLOR);
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
        BlockPos goalPos,
        Vec3d cameraPos
    ) {
        Box goalMarker = new Box(
            goalPos.getX() + GOAL_INSET - cameraPos.x,
            goalPos.getY() - cameraPos.y,
            goalPos.getZ() + GOAL_INSET - cameraPos.z,
            goalPos.getX() + 1.0D - GOAL_INSET - cameraPos.x,
            goalPos.getY() + GOAL_HEIGHT - cameraPos.y,
            goalPos.getZ() + 1.0D - GOAL_INSET - cameraPos.z
        );
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

        renderGoalEdge(matrices, lines, goalMarker.minX, goalMarker.minY, goalMarker.minZ, goalMarker.minX, goalMarker.maxY, goalMarker.minZ);
        renderGoalEdge(matrices, lines, goalMarker.maxX, goalMarker.minY, goalMarker.minZ, goalMarker.maxX, goalMarker.maxY, goalMarker.minZ);
        renderGoalEdge(matrices, lines, goalMarker.minX, goalMarker.minY, goalMarker.maxZ, goalMarker.minX, goalMarker.maxY, goalMarker.maxZ);
        renderGoalEdge(matrices, lines, goalMarker.maxX, goalMarker.minY, goalMarker.maxZ, goalMarker.maxX, goalMarker.maxY, goalMarker.maxZ);

        renderGoalEdge(matrices, lines, goalMarker.minX, goalMarker.minY, goalMarker.minZ, goalMarker.maxX, goalMarker.minY, goalMarker.minZ);
        renderGoalEdge(matrices, lines, goalMarker.minX, goalMarker.minY, goalMarker.maxZ, goalMarker.maxX, goalMarker.minY, goalMarker.maxZ);
        renderGoalEdge(matrices, lines, goalMarker.minX, goalMarker.maxY, goalMarker.minZ, goalMarker.maxX, goalMarker.maxY, goalMarker.minZ);
        renderGoalEdge(matrices, lines, goalMarker.minX, goalMarker.maxY, goalMarker.maxZ, goalMarker.maxX, goalMarker.maxY, goalMarker.maxZ);

        renderGoalEdge(matrices, lines, goalMarker.minX, goalMarker.minY, goalMarker.minZ, goalMarker.minX, goalMarker.minY, goalMarker.maxZ);
        renderGoalEdge(matrices, lines, goalMarker.maxX, goalMarker.minY, goalMarker.minZ, goalMarker.maxX, goalMarker.minY, goalMarker.maxZ);
        renderGoalEdge(matrices, lines, goalMarker.minX, goalMarker.maxY, goalMarker.minZ, goalMarker.minX, goalMarker.maxY, goalMarker.maxZ);
        renderGoalEdge(matrices, lines, goalMarker.maxX, goalMarker.maxY, goalMarker.minZ, goalMarker.maxX, goalMarker.maxY, goalMarker.maxZ);
    }

    private static void renderStepMarkers(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        List<BlockPos> path,
        int pathIndex,
        Vec3d cameraPos
    ) {
        if (path == null || path.size() < 2) {
            return;
        }
        for (int i = 0; i < path.size() - 1; i++) {
            BlockPos step = path.get(i);
            if (step == null) {
                continue;
            }
            if (i < Math.max(0, pathIndex)) {
                continue;
            }
            Box marker = Box.of(cameraRelative(pathPoint(step), cameraPos), 0.34D, 0.34D, 0.34D);
            renderBoxOutline(matrices, consumers, marker, STEP_COLOR);
        }
    }

    private static void renderBreakTargets(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        List<BlockPos> breakTargets,
        Vec3d cameraPos
    ) {
        if (breakTargets == null || breakTargets.isEmpty()) {
            return;
        }
        for (BlockPos breakTarget : breakTargets) {
            if (breakTarget == null) {
                continue;
            }
            Box marker = new Box(
                breakTarget.getX() + 0.02D - cameraPos.x,
                breakTarget.getY() + 0.02D - cameraPos.y,
                breakTarget.getZ() + 0.02D - cameraPos.z,
                breakTarget.getX() + 0.98D - cameraPos.x,
                breakTarget.getY() + 0.98D - cameraPos.y,
                breakTarget.getZ() + 0.98D - cameraPos.z
            );
            renderBoxOutline(matrices, consumers, marker, BREAK_COLOR);
        }
    }

    private static void renderPlaceTargets(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        List<BlockPos> placeTargets,
        Vec3d cameraPos
    ) {
        if (placeTargets == null || placeTargets.isEmpty()) {
            return;
        }
        for (BlockPos placeTarget : placeTargets) {
            if (placeTarget == null) {
                continue;
            }
            Box marker = new Box(
                placeTarget.getX() + 0.02D - cameraPos.x,
                placeTarget.getY() + 0.02D - cameraPos.y,
                placeTarget.getZ() + 0.02D - cameraPos.z,
                placeTarget.getX() + 0.98D - cameraPos.x,
                placeTarget.getY() + 0.98D - cameraPos.y,
                placeTarget.getZ() + 0.98D - cameraPos.z
            );
            renderBoxOutline(matrices, consumers, marker, PLACE_COLOR);
        }
    }

    private static void renderBoxOutline(
        MatrixStack matrices,
        VertexConsumerProvider.Immediate consumers,
        Box marker,
        int color
    ) {
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

        renderGoalEdge(matrices, lines, marker.minX, marker.minY, marker.minZ, marker.maxX, marker.minY, marker.minZ, color);
        renderGoalEdge(matrices, lines, marker.minX, marker.minY, marker.maxZ, marker.maxX, marker.minY, marker.maxZ, color);
        renderGoalEdge(matrices, lines, marker.minX, marker.maxY, marker.minZ, marker.maxX, marker.maxY, marker.minZ, color);
        renderGoalEdge(matrices, lines, marker.minX, marker.maxY, marker.maxZ, marker.maxX, marker.maxY, marker.maxZ, color);

        renderGoalEdge(matrices, lines, marker.minX, marker.minY, marker.minZ, marker.minX, marker.minY, marker.maxZ, color);
        renderGoalEdge(matrices, lines, marker.maxX, marker.minY, marker.minZ, marker.maxX, marker.minY, marker.maxZ, color);
        renderGoalEdge(matrices, lines, marker.minX, marker.maxY, marker.minZ, marker.minX, marker.maxY, marker.maxZ, color);
        renderGoalEdge(matrices, lines, marker.maxX, marker.maxY, marker.minZ, marker.maxX, marker.maxY, marker.maxZ, color);

        renderGoalEdge(matrices, lines, marker.minX, marker.minY, marker.minZ, marker.minX, marker.maxY, marker.minZ, color);
        renderGoalEdge(matrices, lines, marker.maxX, marker.minY, marker.minZ, marker.maxX, marker.maxY, marker.minZ, color);
        renderGoalEdge(matrices, lines, marker.minX, marker.minY, marker.maxZ, marker.minX, marker.maxY, marker.maxZ, color);
        renderGoalEdge(matrices, lines, marker.maxX, marker.minY, marker.maxZ, marker.maxX, marker.maxY, marker.maxZ, color);
    }

    private static double animationPhase() {
        return (System.currentTimeMillis() / 1000.0D * ANIMATION_SPEED) % DASH_CYCLE;
    }

    private static Vec3d pathPoint(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.18D, pos.getZ() + 0.5D);
    }

    private static Vec3d cameraRelative(Vec3d point, Vec3d cameraPos) {
        return point.subtract(cameraPos);
    }

    private static boolean sameRenderPoint(Vec3d a, Vec3d b) {
        return a != null && b != null && a.squaredDistanceTo(b) <= 0.0001D;
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
        renderGoalEdge(matrices, lines, startX, startY, startZ, endX, endY, endZ, GOAL_COLOR);
    }

    private static void renderGoalEdge(
        MatrixStack matrices,
        VertexConsumer lines,
        double startX,
        double startY,
        double startZ,
        double endX,
        double endY,
        double endZ,
        int color
    ) {
        drawLine(matrices, lines, new Vec3d(startX, startY, startZ), new Vec3d(endX, endY, endZ), color);
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
