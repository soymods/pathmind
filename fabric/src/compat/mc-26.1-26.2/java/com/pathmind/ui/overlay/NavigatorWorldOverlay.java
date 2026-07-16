package com.pathmind.ui.overlay;

import com.pathmind.execution.PathmindNavigator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the local navigator's active route directly in the world using Minecraft's debug gizmo system.
 */
public final class NavigatorWorldOverlay {
    private static final int PATH_COLOR = 0xFF66D8FF;
    private static final int CANDIDATE_PATH_COLOR = 0x6687AFC2;
    private static final int GOAL_COLOR = 0xFFFFC857;
    private static final int STEP_COLOR = 0xFF7FD36B;
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

    public static void render(LevelRenderer worldRenderer) {
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

        try (Gizmos.TemporaryCollection ignored = worldRenderer.collectPerFrameGizmos()) {
            renderCandidatePaths(snapshot.candidatePaths());
            renderStepMarkers(snapshot.path(), snapshot.visitedPathIndex());
            renderBreakTargets(snapshot.breakTargets());
            renderPlaceTargets(snapshot.placeTargets());
            renderPath(snapshot.path(), goalPos, snapshot.visitedPathIndex());
            renderGoal(goalPos);
        } catch (Throwable ignored) {
            // Never fail the world renderer because of a debug-style overlay.
        }
    }

    private static void renderCandidatePaths(List<List<BlockPos>> candidatePaths) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client != null ? client.player : null;
        if (candidatePaths == null || candidatePaths.isEmpty() || player == null) {
            return;
        }

        double phase = (System.currentTimeMillis() / 1000.0D * ANIMATION_SPEED) % DASH_CYCLE;
        for (int i = 1; i < candidatePaths.size(); i++) {
            List<BlockPos> candidate = candidatePaths.get(i);
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            List<Vec3> renderPoints = new java.util.ArrayList<>(candidate.size() + 1);
            renderPoints.add(new Vec3(player.getX(), player.getY() + 0.18D, player.getZ()));
            for (BlockPos node : candidate) {
                renderPoints.add(pathPoint(node));
            }
            for (int j = 1; j < renderPoints.size(); j++) {
                renderAnimatedSegment(renderPoints.get(j - 1), renderPoints.get(j), phase, CANDIDATE_PATH_COLOR, 1.2F);
            }
        }
    }

    private static void renderPath(List<BlockPos> path, BlockPos goalPos, int pathIndex) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client != null ? client.player : null;
        if (player == null || goalPos == null) {
            return;
        }

        List<Vec3> renderPoints = new java.util.ArrayList<>((path == null ? 0 : path.size()) + 2);
        renderPoints.add(new Vec3(player.getX(), player.getY() + 0.18D, player.getZ()));
        if (path != null) {
            int startIndex = Math.max(0, Math.min(pathIndex, path.size()));
            for (int i = startIndex; i < path.size(); i++) {
                BlockPos node = path.get(i);
                renderPoints.add(pathPoint(node));
            }
        }
        Vec3 goalPoint = pathPoint(goalPos);
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
            if (i < Math.max(0, pathIndex)) {
                continue;
            }
            Vec3 center = Vec3.atCenterOf(step);
            AABB marker = AABB.ofSize(center.add(0.0D, 0.15D, 0.0D), 0.34D, 0.34D, 0.34D);
            renderBoxOutline(marker, STEP_COLOR, STEP_STROKE_WIDTH);
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
            AABB marker = new AABB(
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
            AABB marker = new AABB(
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

        AABB goalMarker = new AABB(
            goalPos.getX() + GOAL_INSET,
            goalPos.getY(),
            goalPos.getZ() + GOAL_INSET,
            goalPos.getX() + 1.0D - GOAL_INSET,
            goalPos.getY() + 2.0D,
            goalPos.getZ() + 1.0D - GOAL_INSET
        );
        Gizmos.cuboid(goalMarker, GizmoStyle.strokeAndFill(GOAL_COLOR, 2.0F, 0x24FFC857))
            .setAlwaysOnTop()
            .persistForMillis(1);
    }

    private static void renderAnimatedSegment(Vec3 start, Vec3 end, double phase, int color, float width) {
        double segmentLength = start.distanceTo(end);
        if (segmentLength <= 0.0001D) {
            return;
        }

        Vec3 direction = end.subtract(start).normalize();
        double offset = -phase;
        while (offset < segmentLength) {
            double dashStartDistance = Math.max(0.0D, offset);
            double dashEndDistance = Math.min(segmentLength, offset + DASH_LENGTH);
            if (dashEndDistance > 0.0D && dashEndDistance > dashStartDistance) {
                Vec3 dashStart = start.add(direction.scale(dashStartDistance));
                Vec3 dashEnd = start.add(direction.scale(dashEndDistance));
                Gizmos.line(dashStart, dashEnd, color, width)
                    .setAlwaysOnTop()
                    .persistForMillis(1);
            }
            offset += DASH_CYCLE;
        }
    }

    private static void renderBoxOutline(AABB box, int color, float width) {
        Vec3 min = new Vec3(box.minX, box.minY, box.minZ);
        Vec3 max = new Vec3(box.maxX, box.maxY, box.maxZ);

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
        Gizmos.line(new Vec3(startX, startY, startZ), new Vec3(endX, endY, endZ), color, width)
            .setAlwaysOnTop()
            .persistForMillis(1);
    }

    private static Vec3 pathPoint(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.18D, pos.getZ() + 0.5D);
    }

    private static boolean sameRenderPoint(Vec3 a, Vec3 b) {
        return a != null && b != null && a.distanceToSqr(b) <= 0.0001D;
    }
}
