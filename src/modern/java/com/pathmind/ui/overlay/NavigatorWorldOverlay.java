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
    private static final float PATH_LINE_WIDTH = 2.5F;
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
        if (snapshot == null || !snapshot.active()) {
            return;
        }

        BlockPos goalPos = snapshot.resolvedGoalPos() != null ? snapshot.resolvedGoalPos() : snapshot.targetPos();
        if (goalPos == null) {
            return;
        }

        try (GizmoDrawing.CollectorScope ignored = worldRenderer.startDrawingGizmos()) {
            renderCandidatePaths(snapshot.candidatePaths());
            if (snapshot.path().isEmpty()) {
                renderFallbackPath(goalPos);
            } else {
                renderPath(snapshot.path());
            }
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

    private static void renderPath(List<BlockPos> path) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client != null ? client.player : null;
        if (path == null || path.isEmpty() || player == null) {
            return;
        }

        List<Vec3d> renderPoints = new java.util.ArrayList<>(path.size() + 1);
        renderPoints.add(new Vec3d(player.getX(), player.getY() + 0.18D, player.getZ()));
        for (BlockPos node : path) {
            renderPoints.add(pathPoint(node));
        }

        double phase = (System.currentTimeMillis() / 1000.0D * ANIMATION_SPEED) % DASH_CYCLE;
        for (int i = 1; i < renderPoints.size(); i++) {
            renderAnimatedSegment(renderPoints.get(i - 1), renderPoints.get(i), phase, PATH_COLOR, PATH_LINE_WIDTH);
        }
    }

    private static void renderFallbackPath(BlockPos goalPos) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client != null ? client.player : null;
        if (player == null || goalPos == null) {
            return;
        }

        Vec3d start = new Vec3d(player.getX(), player.getY() + 0.18D, player.getZ());
        Vec3d end = pathPoint(goalPos);
        double phase = (System.currentTimeMillis() / 1000.0D * ANIMATION_SPEED) % DASH_CYCLE;
        renderAnimatedSegment(start, end, phase, PATH_COLOR, PATH_LINE_WIDTH);
    }

    private static void renderGoal(BlockPos goalPos) {
        if (goalPos == null) {
            return;
        }

        Vec3d center = Vec3d.ofCenter(goalPos);
        Box goalMarker = Box.of(center.add(0.0D, 0.5D, 0.0D), 1.0D, 2.0D, 1.0D);
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

    private static Vec3d pathPoint(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.18D, pos.getZ() + 0.5D);
    }
}
