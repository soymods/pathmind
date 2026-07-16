package com.pathmind.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class PathmindNeoForgeWorldOverlay {
    private static final int PATH_COLOR = 0xFF66D8FF;
    private static final int CANDIDATE_PATH_COLOR = 0x6687AFC2;
    private static final int GOAL_COLOR = 0xFFFFC857;
    private static final int GOAL_FILL_COLOR = 0x24FFC857;
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
    private static final int GIZMO_PERSIST_MS = 80;

    private static boolean initialized;
    private static boolean unavailable;
    private static Method collectPerFrameGizmosMethod;
    private static Method navigatorGetInstanceMethod;
    private static Method navigatorGetSnapshotMethod;
    private static Method gizmosLineMethod;
    private static Method gizmosCuboidMethod;
    private static Method gizmoStyleStrokeMethod;
    private static Method gizmoStyleStrokeAndFillMethod;
    private static Method gizmoPropertiesAlwaysOnTopMethod;
    private static Method gizmoPropertiesPersistMethod;
    private static Class<?> snapshotClass;
    private static Method snapshotStateMethod;
    private static Method snapshotTargetPosMethod;
    private static Method snapshotVisitedPathIndexMethod;
    private static Method snapshotBreakTargetsMethod;
    private static Method snapshotPlaceTargetsMethod;
    private static Method snapshotPathMethod;
    private static Method snapshotCandidatePathsMethod;

    private PathmindNeoForgeWorldOverlay() {
    }

    static boolean render(RenderLevelStageEvent event) throws ReflectiveOperationException {
        if (event == null || !ensureInitialized(event)) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || event.getLevelRenderer() == null) {
            return false;
        }

        Object navigator = navigatorGetInstanceMethod.invoke(null);
        Object snapshot = navigatorGetSnapshotMethod.invoke(navigator);
        if (snapshot == null) {
            return false;
        }
        ensureSnapshotMethods(snapshot.getClass());

        String state = String.valueOf(snapshotStateMethod.invoke(snapshot));
        if (!"PATHING".equals(state) && !"PREVIEW".equals(state)) {
            return false;
        }

        BlockPos goalPos = asBlockPos(snapshotTargetPosMethod.invoke(snapshot));
        if (goalPos == null) {
            return false;
        }

        Object collection = collectPerFrameGizmosMethod.invoke(event.getLevelRenderer());
        try {
            renderCandidatePaths(client, asList(snapshotCandidatePathsMethod.invoke(snapshot)));
            renderStepMarkers(asList(snapshotPathMethod.invoke(snapshot)), asInt(snapshotVisitedPathIndexMethod.invoke(snapshot)));
            renderBreakTargets(asList(snapshotBreakTargetsMethod.invoke(snapshot)));
            renderPlaceTargets(asList(snapshotPlaceTargetsMethod.invoke(snapshot)));
            renderPath(client, asList(snapshotPathMethod.invoke(snapshot)), goalPos, asInt(snapshotVisitedPathIndexMethod.invoke(snapshot)));
            renderGoal(goalPos);
            return true;
        } finally {
            if (collection instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                    // The overlay is best-effort debug rendering; never break level rendering during cleanup.
                }
            }
        }
    }

    private static boolean ensureInitialized(RenderLevelStageEvent event) throws ReflectiveOperationException {
        if (initialized) {
            return true;
        }
        if (unavailable || event == null || event.getLevelRenderer() == null) {
            return false;
        }

        try {
            collectPerFrameGizmosMethod = event.getLevelRenderer().getClass().getMethod("collectPerFrameGizmos");

            Class<?> navigatorClass = Class.forName("com.pathmind.execution.PathmindNavigator");
            navigatorGetInstanceMethod = navigatorClass.getMethod("getInstance");
            navigatorGetSnapshotMethod = navigatorClass.getMethod("getSnapshot");

            Class<?> gizmosClass = Class.forName("net.minecraft.gizmos.Gizmos");
            Class<?> gizmoStyleClass = Class.forName("net.minecraft.gizmos.GizmoStyle");
            Class<?> gizmoPropertiesClass = Class.forName("net.minecraft.gizmos.GizmoProperties");
            gizmosLineMethod = gizmosClass.getMethod("line", Vec3.class, Vec3.class, int.class, float.class);
            gizmosCuboidMethod = gizmosClass.getMethod("cuboid", AABB.class, gizmoStyleClass, boolean.class);
            gizmoStyleStrokeMethod = gizmoStyleClass.getMethod("stroke", int.class, float.class);
            gizmoStyleStrokeAndFillMethod = gizmoStyleClass.getMethod("strokeAndFill", int.class, float.class, int.class);
            gizmoPropertiesAlwaysOnTopMethod = gizmoPropertiesClass.getMethod("setAlwaysOnTop");
            gizmoPropertiesPersistMethod = gizmoPropertiesClass.getMethod("persistForMillis", int.class);

            initialized = true;
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            unavailable = true;
            return false;
        }
    }

    private static void ensureSnapshotMethods(Class<?> currentSnapshotClass) throws NoSuchMethodException {
        if (currentSnapshotClass == snapshotClass) {
            return;
        }
        snapshotClass = currentSnapshotClass;
        snapshotStateMethod = currentSnapshotClass.getMethod("state");
        snapshotTargetPosMethod = currentSnapshotClass.getMethod("targetPos");
        snapshotVisitedPathIndexMethod = currentSnapshotClass.getMethod("visitedPathIndex");
        snapshotBreakTargetsMethod = currentSnapshotClass.getMethod("breakTargets");
        snapshotPlaceTargetsMethod = currentSnapshotClass.getMethod("placeTargets");
        snapshotPathMethod = currentSnapshotClass.getMethod("path");
        snapshotCandidatePathsMethod = currentSnapshotClass.getMethod("candidatePaths");
    }

    private static void renderCandidatePaths(Minecraft client, List<?> candidatePaths) throws ReflectiveOperationException {
        if (candidatePaths.isEmpty() || client.player == null) {
            return;
        }

        double phase = (System.currentTimeMillis() / 1000.0D * ANIMATION_SPEED) % DASH_CYCLE;
        for (int i = 1; i < candidatePaths.size(); i++) {
            List<?> candidate = asList(candidatePaths.get(i));
            if (candidate.isEmpty()) {
                continue;
            }
            List<Vec3> renderPoints = new ArrayList<>(candidate.size() + 1);
            renderPoints.add(new Vec3(client.player.getX(), client.player.getY() + 0.18D, client.player.getZ()));
            for (Object nodeObject : candidate) {
                BlockPos node = asBlockPos(nodeObject);
                if (node != null) {
                    renderPoints.add(pathPoint(node));
                }
            }
            for (int j = 1; j < renderPoints.size(); j++) {
                renderAnimatedSegment(renderPoints.get(j - 1), renderPoints.get(j), phase, CANDIDATE_PATH_COLOR, 1.2F);
            }
        }
    }

    private static void renderPath(Minecraft client, List<?> path, BlockPos goalPos, int pathIndex) throws ReflectiveOperationException {
        if (client.player == null || goalPos == null) {
            return;
        }

        List<Vec3> renderPoints = new ArrayList<>(path.size() + 2);
        renderPoints.add(new Vec3(client.player.getX(), client.player.getY() + 0.18D, client.player.getZ()));
        int startIndex = Math.max(0, Math.min(pathIndex, path.size()));
        for (int i = startIndex; i < path.size(); i++) {
            BlockPos node = asBlockPos(path.get(i));
            if (node != null) {
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

    private static void renderStepMarkers(List<?> path, int pathIndex) throws ReflectiveOperationException {
        if (path.isEmpty()) {
            return;
        }
        int startIndex = Math.max(0, pathIndex);
        for (int i = startIndex; i < path.size() - 1; i++) {
            BlockPos step = asBlockPos(path.get(i));
            if (step == null) {
                continue;
            }
            renderBox(AABB.ofSize(pathPoint(step).add(0.0D, -0.03D, 0.0D), 0.34D, 0.34D, 0.34D), STEP_COLOR, STEP_STROKE_WIDTH, 0);
        }
    }

    private static void renderBreakTargets(List<?> breakTargets) throws ReflectiveOperationException {
        for (Object targetObject : breakTargets) {
            BlockPos target = asBlockPos(targetObject);
            if (target != null) {
                renderBox(fullBlockBox(target, 0.02D), BREAK_COLOR, BREAK_STROKE_WIDTH, 0);
            }
        }
    }

    private static void renderPlaceTargets(List<?> placeTargets) throws ReflectiveOperationException {
        for (Object targetObject : placeTargets) {
            BlockPos target = asBlockPos(targetObject);
            if (target != null) {
                renderBox(fullBlockBox(target, 0.02D), PLACE_COLOR, BREAK_STROKE_WIDTH, 0);
            }
        }
    }

    private static void renderGoal(BlockPos goalPos) throws ReflectiveOperationException {
        AABB goalMarker = new AABB(
            goalPos.getX() + GOAL_INSET,
            goalPos.getY(),
            goalPos.getZ() + GOAL_INSET,
            goalPos.getX() + 1.0D - GOAL_INSET,
            goalPos.getY() + 2.0D,
            goalPos.getZ() + 1.0D - GOAL_INSET
        );
        renderBox(goalMarker, GOAL_COLOR, 2.0F, GOAL_FILL_COLOR);
    }

    private static void renderAnimatedSegment(Vec3 start, Vec3 end, double phase, int color, float width) throws ReflectiveOperationException {
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
                renderLine(dashStart, dashEnd, color, width);
            }
            offset += DASH_CYCLE;
        }
    }

    private static void renderLine(Vec3 start, Vec3 end, int color, float width) throws ReflectiveOperationException {
        Object properties = gizmosLineMethod.invoke(null, start, end, color, width);
        makeVisible(properties);
    }

    private static void renderBox(AABB box, int strokeColor, float strokeWidth, int fillColor) throws ReflectiveOperationException {
        Object style = fillColor == 0
            ? gizmoStyleStrokeMethod.invoke(null, strokeColor, strokeWidth)
            : gizmoStyleStrokeAndFillMethod.invoke(null, strokeColor, strokeWidth, fillColor);
        Object properties = gizmosCuboidMethod.invoke(null, box, style, false);
        makeVisible(properties);
    }

    private static void makeVisible(Object properties) throws ReflectiveOperationException {
        if (properties == null) {
            return;
        }
        Object visible = gizmoPropertiesAlwaysOnTopMethod.invoke(properties);
        gizmoPropertiesPersistMethod.invoke(visible != null ? visible : properties, GIZMO_PERSIST_MS);
    }

    private static Vec3 pathPoint(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.18D, pos.getZ() + 0.5D);
    }

    private static AABB fullBlockBox(BlockPos pos, double inset) {
        return new AABB(
            pos.getX() + inset,
            pos.getY() + inset,
            pos.getZ() + inset,
            pos.getX() + 1.0D - inset,
            pos.getY() + 1.0D - inset,
            pos.getZ() + 1.0D - inset
        );
    }

    private static boolean sameRenderPoint(Vec3 a, Vec3 b) {
        return a != null && b != null && a.distanceToSqr(b) <= 0.0001D;
    }

    private static BlockPos asBlockPos(Object value) {
        return value instanceof BlockPos pos ? pos : null;
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }
}
