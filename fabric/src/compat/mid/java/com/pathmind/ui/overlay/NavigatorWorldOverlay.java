package com.pathmind.ui.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pathmind.execution.PathmindNavigator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
        PoseStack matrices,
        MultiBufferSource.BufferSource consumers,
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

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client != null ? client.player : null;
        if (player == null) {
            return;
        }

        Vec3 cameraPos = new Vec3(cameraX, cameraY, cameraZ);
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
        PoseStack matrices,
        MultiBufferSource.BufferSource consumers,
        LocalPlayer player,
        List<List<BlockPos>> candidatePaths,
        Vec3 cameraPos
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

            List<Vec3> points = new ArrayList<>(candidate.size() + 1);
            points.add(cameraRelative(new Vec3(player.getX(), player.getY() + 0.18D, player.getZ()), cameraPos));
            for (BlockPos node : candidate) {
                points.add(cameraRelative(pathPoint(node), cameraPos));
            }
            renderDashedSegments(matrices, consumers, points, phase, CANDIDATE_PATH_COLOR);
        }
    }

    private static void renderPath(
        PoseStack matrices,
        MultiBufferSource.BufferSource consumers,
        LocalPlayer player,
        List<BlockPos> path,
        BlockPos goalPos,
        int pathIndex,
        Vec3 cameraPos
    ) {
        List<Vec3> points = new ArrayList<>((path == null ? 0 : path.size()) + 2);
        points.add(cameraRelative(new Vec3(player.getX(), player.getY() + 0.18D, player.getZ()), cameraPos));
        if (path != null) {
            int startIndex = Math.max(0, Math.min(pathIndex, path.size()));
            for (int i = startIndex; i < path.size(); i++) {
                BlockPos node = path.get(i);
                points.add(cameraRelative(pathPoint(node), cameraPos));
            }
        }
        Vec3 goalPoint = cameraRelative(pathPoint(goalPos), cameraPos);
        if (points.isEmpty() || !sameRenderPoint(points.get(points.size() - 1), goalPoint)) {
            points.add(goalPoint);
        }
        if (points.size() < 2) {
            return;
        }
        renderDashedSegments(matrices, consumers, points, animationPhase(), PATH_COLOR);
    }

    private static void renderDashedSegments(
        PoseStack matrices,
        MultiBufferSource.BufferSource consumers,
        List<Vec3> points,
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
        PoseStack matrices,
        MultiBufferSource.BufferSource consumers,
        Vec3 start,
        Vec3 end,
        double phase,
        int color
    ) {
        double segmentLength = start.distanceTo(end);
        if (segmentLength <= 0.0001D) {
            return;
        }

        VertexConsumer lines = consumers.getBuffer(RenderType.lines());
        Vec3 direction = end.subtract(start).normalize();
        double offset = -phase;
        while (offset < segmentLength) {
            double dashStartDistance = Math.max(0.0D, offset);
            double dashEndDistance = Math.min(segmentLength, offset + DASH_LENGTH);
            if (dashEndDistance > dashStartDistance) {
                Vec3 dashStart = start.add(direction.scale(dashStartDistance));
                Vec3 dashEnd = start.add(direction.scale(dashEndDistance));
                drawLine(matrices, lines, dashStart, dashEnd, color);
            }
            offset += DASH_CYCLE;
        }
    }

    private static void renderGoal(
        PoseStack matrices,
        MultiBufferSource.BufferSource consumers,
        BlockPos goalPos,
        Vec3 cameraPos
    ) {
        AABB goalMarker = new AABB(
            goalPos.getX() + GOAL_INSET - cameraPos.x,
            goalPos.getY() - cameraPos.y,
            goalPos.getZ() + GOAL_INSET - cameraPos.z,
            goalPos.getX() + 1.0D - GOAL_INSET - cameraPos.x,
            goalPos.getY() + GOAL_HEIGHT - cameraPos.y,
            goalPos.getZ() + 1.0D - GOAL_INSET - cameraPos.z
        );
        VertexConsumer lines = consumers.getBuffer(RenderType.lines());

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
        PoseStack matrices,
        MultiBufferSource.BufferSource consumers,
        List<BlockPos> path,
        int pathIndex,
        Vec3 cameraPos
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
            AABB marker = AABB.ofSize(cameraRelative(pathPoint(step), cameraPos), 0.34D, 0.34D, 0.34D);
            renderBoxOutline(matrices, consumers, marker, STEP_COLOR);
        }
    }

    private static void renderBreakTargets(
        PoseStack matrices,
        MultiBufferSource.BufferSource consumers,
        List<BlockPos> breakTargets,
        Vec3 cameraPos
    ) {
        if (breakTargets == null || breakTargets.isEmpty()) {
            return;
        }
        for (BlockPos breakTarget : breakTargets) {
            if (breakTarget == null) {
                continue;
            }
            AABB marker = new AABB(
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
        PoseStack matrices,
        MultiBufferSource.BufferSource consumers,
        List<BlockPos> placeTargets,
        Vec3 cameraPos
    ) {
        if (placeTargets == null || placeTargets.isEmpty()) {
            return;
        }
        for (BlockPos placeTarget : placeTargets) {
            if (placeTarget == null) {
                continue;
            }
            AABB marker = new AABB(
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
        PoseStack matrices,
        MultiBufferSource.BufferSource consumers,
        AABB marker,
        int color
    ) {
        VertexConsumer lines = consumers.getBuffer(RenderType.lines());

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

    private static Vec3 pathPoint(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.18D, pos.getZ() + 0.5D);
    }

    private static Vec3 cameraRelative(Vec3 point, Vec3 cameraPos) {
        return point.subtract(cameraPos);
    }

    private static boolean sameRenderPoint(Vec3 a, Vec3 b) {
        return a != null && b != null && a.distanceToSqr(b) <= 0.0001D;
    }

    private static void renderGoalEdge(
        PoseStack matrices,
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
        PoseStack matrices,
        VertexConsumer lines,
        double startX,
        double startY,
        double startZ,
        double endX,
        double endY,
        double endZ,
        int color
    ) {
        drawLine(matrices, lines, new Vec3(startX, startY, startZ), new Vec3(endX, endY, endZ), color);
    }

    private static void drawLine(PoseStack matrices, VertexConsumer lines, Vec3 start, Vec3 end, int color) {
        PoseStack.Pose entry = matrices.last();
        Vec3 delta = end.subtract(start);
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

        lines.addVertex(entry, (float) start.x, (float) start.y, (float) start.z)
            .setColor(red, green, blue, alpha)
            .setNormal(entry, nx, ny, nz);
        lines.addVertex(entry, (float) end.x, (float) end.y, (float) end.z)
            .setColor(red, green, blue, alpha)
            .setNormal(entry, nx, ny, nz);
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
