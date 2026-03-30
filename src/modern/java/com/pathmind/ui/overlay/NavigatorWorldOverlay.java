package com.pathmind.ui.overlay;

import com.pathmind.execution.PathmindNavigator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.util.List;

/**
 * Draws the local navigator's active route directly in the world using Minecraft's debug gizmo system.
 */
public final class NavigatorWorldOverlay {
    private static final int PATH_COLOR = 0xFF66D8FF;
    private static final int CANDIDATE_PATH_COLOR = 0x6687AFC2;
    private static final int GOAL_COLOR = 0xFFFFC857;
    private static final int STEP_COLOR = 0xFF7FD36B;
    private static final int VISITED_STEP_COLOR = 0xFF3E8E7E;
    private static final int BREAK_COLOR = 0xFFFF5A4F;
    private static final int PLACE_COLOR = 0xFFC47BFF;
    private static final float PATH_LINE_WIDTH = 2.5F;
    private static final float STEP_STROKE_WIDTH = 1.4F;
    private static final float BREAK_STROKE_WIDTH = 1.8F;
    private static final double GOAL_INSET = 0.02D;
    private static final double DASH_LENGTH = 0.42D;
    private static final double DASH_GAP = 0.24D;
    private static final double DASH_CYCLE = DASH_LENGTH + DASH_GAP;
    private static final double ANIMATION_SPEED = 1.45D;

    private NavigatorWorldOverlay() {
    }

    public static void render(WorldRenderer worldRenderer) {
        if (worldRenderer == null) {
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

        try (GizmoDrawing.CollectorScope ignored = worldRenderer.startDrawingGizmos()) {
            renderCandidatePaths(snapshot.candidatePaths());
            renderStepMarkers(snapshot.path(), snapshot.pathIndex());
            renderBreakTargets(snapshot.breakTargets());
            renderPlaceTargets(snapshot.placeTargets());
            renderPath(snapshot.path(), goalPos);
            renderGoal(goalPos);
        } catch (Throwable ignored) {
            // Never fail the world renderer because of a debug-style overlay.
        }
    }

    private static void renderCandidatePaths(List<List<BlockPos>> candidatePaths) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client != null ? client.player : null;
        if (candidatePaths == null || candidatePaths.isEmpty() || player == null) {
            return;
        }

        double phase = (System.currentTimeMillis() / 1000.0D * ANIMATION_SPEED) % DASH_CYCLE;
        for (int i = 1; i < candidatePaths.size(); i++) {
            List<BlockPos> candidate = candidatePaths.get(i);
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            List<Vec3d> renderPoints = new java.util.ArrayList<>(candidate.size() + 1);
            renderPoints.add(new Vec3d(player.getX(), player.getY() + 0.18D, player.getZ()));
            for (BlockPos node : candidate) {
                renderPoints.add(pathPoint(node));
            }
            for (int j = 1; j < renderPoints.size(); j++) {
                renderAnimatedSegment(renderPoints.get(j - 1), renderPoints.get(j), phase, CANDIDATE_PATH_COLOR, 1.2F);
            }
        }
    }

    private static void renderPath(List<BlockPos> path, BlockPos goalPos) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client != null ? client.player : null;
        if (player == null || goalPos == null) {
            return;
        }

        List<Vec3d> renderPoints = new java.util.ArrayList<>((path == null ? 0 : path.size()) + 2);
        renderPoints.add(new Vec3d(player.getX(), player.getY() + 0.18D, player.getZ()));
        if (path != null) {
            for (BlockPos node : path) {
                renderPoints.add(pathPoint(node));
            }
        }
        Vec3d goalPoint = pathPoint(goalPos);
        if (renderPoints.isEmpty() || !sameRenderPoint(renderPoints.get(renderPoints.size() - 1), goalPoint)) {
            renderPoints.add(goalPoint);
        }

        if (renderPoints.size() < 2) {
            return;
        }

        double phase = (System.currentTimeMillis() / 1000.0D * ANIMATION_SPEED) % DASH_CYCLE;
        for (int i = 1; i < renderPoints.size(); i++) {
            renderAnimatedSegment(renderPoints.get(i - 1), renderPoints.get(i), phase, PATH_COLOR, PATH_LINE_WIDTH);
        }
    }

    private static void renderStepMarkers(List<BlockPos> path, int pathIndex) {
        if (path == null || path.isEmpty()) {
            return;
        }
        for (int i = 0; i < path.size() - 1; i++) {
            BlockPos step = path.get(i);
            if (step == null) {
                continue;
            }
            Vec3d center = Vec3d.ofCenter(step);
            Box marker = Box.of(center.add(0.0D, 0.15D, 0.0D), 0.34D, 0.34D, 0.34D);
            int color = i < Math.max(0, pathIndex) ? VISITED_STEP_COLOR : STEP_COLOR;
            renderBoxOutline(marker, color, STEP_STROKE_WIDTH);
        }
    }

    private static void renderBreakTargets(List<BlockPos> breakTargets) {
        if (breakTargets == null || breakTargets.isEmpty()) {
            return;
        }
        for (BlockPos breakTarget : breakTargets) {
            if (breakTarget == null) {
                continue;
            }
            Box marker = new Box(
                breakTarget.getX() + 0.02D,
                breakTarget.getY() + 0.02D,
                breakTarget.getZ() + 0.02D,
                breakTarget.getX() + 0.98D,
                breakTarget.getY() + 0.98D,
                breakTarget.getZ() + 0.98D
            );
            renderBoxOutline(marker, BREAK_COLOR, BREAK_STROKE_WIDTH);
        }
    }

    private static void renderPlaceTargets(List<BlockPos> placeTargets) {
        if (placeTargets == null || placeTargets.isEmpty()) {
            return;
        }
        for (BlockPos placeTarget : placeTargets) {
            if (placeTarget == null) {
                continue;
            }
            Box marker = new Box(
                placeTarget.getX() + 0.02D,
                placeTarget.getY() + 0.02D,
                placeTarget.getZ() + 0.02D,
                placeTarget.getX() + 0.98D,
                placeTarget.getY() + 0.98D,
                placeTarget.getZ() + 0.98D
            );
            renderBoxOutline(marker, PLACE_COLOR, BREAK_STROKE_WIDTH);
        }
    }

    private static void renderGoal(BlockPos goalPos) {
        if (goalPos == null) {
            return;
        }

        Box goalMarker = new Box(
            goalPos.getX() + GOAL_INSET,
            goalPos.getY(),
            goalPos.getZ() + GOAL_INSET,
            goalPos.getX() + 1.0D - GOAL_INSET,
            goalPos.getY() + 2.0D,
            goalPos.getZ() + 1.0D - GOAL_INSET
        );
        GizmoDrawing.box(goalMarker, DrawStyle.filledAndStroked(GOAL_COLOR, 2.0F, 0x24FFC857))
            .ignoreOcclusion()
            .withLifespan(1);
    }

    private static void renderAnimatedSegment(Vec3d start, Vec3d end, double phase, int color, float width) {
        double segmentLength = start.distanceTo(end);
        if (segmentLength <= 0.0001D) {
            return;
        }

        Vec3d direction = end.subtract(start).normalize();
        double offset = -phase;
        while (offset < segmentLength) {
            double dashStartDistance = Math.max(0.0D, offset);
            double dashEndDistance = Math.min(segmentLength, offset + DASH_LENGTH);
            if (dashEndDistance > 0.0D && dashEndDistance > dashStartDistance) {
                Vec3d dashStart = start.add(direction.multiply(dashStartDistance));
                Vec3d dashEnd = start.add(direction.multiply(dashEndDistance));
                GizmoDrawing.line(dashStart, dashEnd, color, width)
                    .ignoreOcclusion()
                    .withLifespan(1);
            }
            offset += DASH_CYCLE;
        }
    }

    private static void renderBoxOutline(Box box, int color, float width) {
        Vec3d min = new Vec3d(box.minX, box.minY, box.minZ);
        Vec3d max = new Vec3d(box.maxX, box.maxY, box.maxZ);

        renderOutlineEdge(min.x, min.y, min.z, max.x, min.y, min.z, color, width);
        renderOutlineEdge(min.x, min.y, max.z, max.x, min.y, max.z, color, width);
        renderOutlineEdge(min.x, max.y, min.z, max.x, max.y, min.z, color, width);
        renderOutlineEdge(min.x, max.y, max.z, max.x, max.y, max.z, color, width);

        renderOutlineEdge(min.x, min.y, min.z, min.x, min.y, max.z, color, width);
        renderOutlineEdge(max.x, min.y, min.z, max.x, min.y, max.z, color, width);
        renderOutlineEdge(min.x, max.y, min.z, min.x, max.y, max.z, color, width);
        renderOutlineEdge(max.x, max.y, min.z, max.x, max.y, max.z, color, width);

        renderOutlineEdge(min.x, min.y, min.z, min.x, max.y, min.z, color, width);
        renderOutlineEdge(max.x, min.y, min.z, max.x, max.y, min.z, color, width);
        renderOutlineEdge(min.x, min.y, max.z, min.x, max.y, max.z, color, width);
        renderOutlineEdge(max.x, min.y, max.z, max.x, max.y, max.z, color, width);
    }

    private static void renderOutlineEdge(
        double startX,
        double startY,
        double startZ,
        double endX,
        double endY,
        double endZ,
        int color,
        float width
    ) {
        GizmoDrawing.line(new Vec3d(startX, startY, startZ), new Vec3d(endX, endY, endZ), color, width)
            .ignoreOcclusion()
            .withLifespan(1);
    }

    private static Vec3d pathPoint(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.18D, pos.getZ() + 0.5D);
    }

    private static boolean sameRenderPoint(Vec3d a, Vec3d b) {
        return a != null && b != null && a.squaredDistanceTo(b) <= 0.0001D;
    }
}
