package com.pathmind.schematic;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Shared, non-mutating world preview for schematic commands and editor nodes. */
public final class SchematicPreview {
    private static final long PREVIEW_DURATION_MS = 60_000L;
    private static volatile Snapshot active;

    private SchematicPreview() {
    }

    public static Result show(Minecraft client, String fileName, BlockPos origin) {
        if (client == null || client.gameDirectory == null || client.level == null) {
            return new Result(false, "A loaded world is required to preview a schematic.");
        }
        if (fileName == null || fileName.isBlank()) {
            return new Result(false, "Select a schematic before previewing.");
        }
        if (origin == null) {
            return new Result(false, "Set the schematic placement coordinates before previewing.");
        }
        java.util.Optional<Path> file = SchematicFiles.resolve(client.gameDirectory.toPath(), fileName.trim());
        if (file.isEmpty()) {
            return new Result(false, "Schematic not found: " + fileName.trim());
        }
        try {
            SchematicBuildPlan plan = SchematicLoader.load(file.get());
            active = new Snapshot(plan, origin.immutable(), System.currentTimeMillis() + PREVIEW_DURATION_MS, false);
            SchematicBuildPlan.Dimensions size = plan.dimensions();
            return new Result(true, "Previewing " + fileName.trim() + " at " + format(origin) + " ("
                + size.width() + "x" + size.height() + "x" + size.length() + ") for 60 seconds.");
        } catch (SchematicLoadException exception) {
            return new Result(false, "Could not load schematic: " + exception.getMessage());
        }
    }

    public static Snapshot snapshot() {
        Snapshot current = active;
        if (current != null && System.currentTimeMillis() > current.expiresAtMs()) {
            active = null;
            return null;
        }
        return current;
    }

    /** Keeps the preview alive for the complete lifecycle of an active build. */
    public static void showBuild(SchematicBuildPlan plan, BlockPos origin) {
        if (plan != null && origin != null) {
            active = new Snapshot(plan, origin.immutable(), Long.MAX_VALUE, true);
        }
    }

    public static void clearBuild() {
        Snapshot current = active;
        if (current != null && current.buildOwned()) {
            active = null;
        }
    }

    public static void clear() {
        active = null;
    }

    private static String format(BlockPos position) {
        return position.getX() + " " + position.getY() + " " + position.getZ();
    }

    public record Snapshot(SchematicBuildPlan plan, BlockPos origin, long expiresAtMs, boolean buildOwned) {
    }

    public record Result(boolean success, String message) {
    }
}
