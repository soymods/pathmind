package com.pathmind.nodes;

import com.pathmind.util.BlockSelection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

final class NodeWorldTargetResolver {
    private static final Pattern UNSAFE_RESOURCE_ID_PATTERN = Pattern.compile("[^a-z0-9_:/.-]");

    private final Node owner;

    NodeWorldTargetResolver(Node owner) {
        this.owner = owner;
    }

    static boolean isAnySelectionValue(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty()
            || "any".equalsIgnoreCase(trimmed)
            || "any state".equalsIgnoreCase(trimmed);
    }

    String sanitizeResourceId(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT).replace(' ', '_');
        int bracketIndex = lower.indexOf('[');
        if (bracketIndex >= 0) {
            lower = lower.substring(0, bracketIndex);
        }
        String sanitized = UNSAFE_RESOURCE_ID_PATTERN.matcher(lower).replaceAll("");
        int firstColon = sanitized.indexOf(':');
        if (firstColon != -1) {
            int nextColon = sanitized.indexOf(':', firstColon + 1);
            if (nextColon != -1) {
                sanitized = sanitized.substring(0, firstColon + 1) + sanitized.substring(firstColon + 1).replace(':', '_');
            }
        }
        return sanitized;
    }

    String normalizeResourceId(String value, String defaultNamespace) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (isAnySelectionValue(trimmed)) {
            return "";
        }
        if (!trimmed.contains(":")) {
            return defaultNamespace + ":" + trimmed;
        }
        return trimmed;
    }

    List<BlockSelection> resolveBlocksFromParameter(Node parameterNode) {
        List<BlockSelection> selections = new ArrayList<>();
        String primary = owner.getBlockParameterValue(parameterNode);
        String listValue = Node.getParameterString(parameterNode, "Blocks");
        for (String entry : splitMultiValueList(listValue)) {
            addBlockSelection(selections, entry);
        }
        for (String entry : splitMultiValueList(primary)) {
            addBlockSelection(selections, entry);
        }
        return selections;
    }

    private void addBlockSelection(List<BlockSelection> selections, String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return;
        }
        if (isAnySelectionValue(rawValue)) {
            return;
        }
        BlockSelection.parse(rawValue).ifPresent(selection -> {
            if (selection.getBlock() != null) {
                boolean exists = selections.stream().anyMatch(existing -> existing.asString().equals(selection.asString()));
                if (!exists) {
                    selections.add(selection);
                }
            }
        });
    }

    List<String> resolveItemIdsFromParameter(Node parameterNode) {
        List<String> itemIds = new ArrayList<>();
        if (parameterNode == null) {
            return itemIds;
        }
        String listValue = Node.getParameterString(parameterNode, "Items");
        for (String entry : splitMultiValueList(listValue)) {
            addItemIdentifier(itemIds, entry);
        }
        for (String entry : splitMultiValueList(Node.getParameterString(parameterNode, "Item"))) {
            addItemIdentifier(itemIds, entry);
        }
        return itemIds;
    }

    String resolveTradeKeyFromParameter(Node parameterNode) {
        if (parameterNode == null) {
            return "";
        }
        String trade = Node.getParameterString(parameterNode, "Trade");
        if (trade != null && !trade.isEmpty()) {
            return trade;
        }
        String legacy = Node.getParameterString(parameterNode, "Item");
        return legacy != null ? legacy : "";
    }

    List<String> resolveEntityIdsFromParameter(Node parameterNode) {
        List<String> entityIds = new ArrayList<>();
        if (parameterNode == null) {
            return entityIds;
        }
        for (String entry : splitMultiValueList(Node.getParameterString(parameterNode, "Entity"))) {
            addEntityIdentifier(entityIds, entry);
        }
        return entityIds;
    }

    void addItemIdentifier(List<String> itemIds, String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return;
        }
        if (isAnySelectionValue(rawValue)) {
            return;
        }
        String sanitized = sanitizeResourceId(rawValue);
        if (sanitized == null || sanitized.isEmpty()) {
            return;
        }
        String normalized = normalizeResourceId(sanitized, "minecraft");
        if (!itemIds.contains(normalized)) {
            itemIds.add(normalized);
        }
    }

    private void addEntityIdentifier(List<String> entityIds, String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return;
        }
        if (isAnySelectionValue(rawValue)) {
            return;
        }
        String sanitized = sanitizeResourceId(rawValue);
        if (sanitized == null || sanitized.isEmpty()) {
            return;
        }
        String normalized = normalizeResourceId(sanitized, "minecraft");
        if (!entityIds.contains(normalized)) {
            entityIds.add(normalized);
        }
    }

    List<String> splitMultiValueList(String rawValue) {
        if (rawValue == null) {
            return Collections.emptyList();
        }
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketDepth = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '[') {
                bracketDepth++;
            } else if (c == ']') {
                bracketDepth = Math.max(0, bracketDepth - 1);
            }
            if ((c == ',' || c == ';') && bracketDepth == 0) {
                String entry = current.toString().trim();
                if (!entry.isEmpty()) {
                    parts.add(entry);
                }
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String entry = current.toString().trim();
        if (!entry.isEmpty()) {
            parts.add(entry);
        }
        return parts;
    }

    Optional<BlockPos> findNearestBlock(net.minecraft.client.Minecraft client, List<BlockSelection> selections, double range) {
        if (client == null || client.player == null || client.level == null || selections == null || selections.isEmpty()) {
            return Optional.empty();
        }
        int radius = Math.max(1, Math.min((int) Math.ceil(range), 64));
        BlockPos playerPos = client.player.blockPosition();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;
        double maxDistanceSq = range * range;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double offsetDistanceSq = dx * dx + dy * dy + dz * dz;
                    if (offsetDistanceSq > maxDistanceSq) {
                        continue;
                    }
                    mutable.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    if (!client.level.hasChunk(Math.floorDiv(mutable.getX(), 16), Math.floorDiv(mutable.getZ(), 16))) {
                        continue;
                    }
                    BlockState state = client.level.getBlockState(mutable);
                    if (state.isAir()) {
                        continue;
                    }
                    boolean matches = false;
                    for (BlockSelection selection : selections) {
                        if (selection.matches(state)) {
                            matches = true;
                            break;
                        }
                    }
                    if (!matches) {
                        continue;
                    }
                    double distance = mutable.distSqr(playerPos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPos = mutable.immutable();
                    }
                }
            }
        }

        return Optional.ofNullable(bestPos);
    }

    List<BlockPos> findBlocksWithinRange(net.minecraft.client.Minecraft client, List<BlockSelection> selections, double range) {
        return findBlocksWithinRange(client, selections, range, 0);
    }

    List<BlockPos> findBlocksWithinRange(net.minecraft.client.Minecraft client, List<BlockSelection> selections, double range, int maxResults) {
        if (client == null || client.player == null || client.level == null || selections == null || selections.isEmpty()) {
            return Collections.emptyList();
        }
        int resultLimit = Math.max(0, maxResults);
        int radius = Math.max(1, (int) Math.ceil(range));
        BlockPos playerPos = client.player.blockPosition();
        List<BlockPos> matches = new ArrayList<>();
        int minChunkX = Math.floorDiv(playerPos.getX() - radius, 16);
        int maxChunkX = Math.floorDiv(playerPos.getX() + radius, 16);
        int minChunkZ = Math.floorDiv(playerPos.getZ() - radius, 16);
        int maxChunkZ = Math.floorDiv(playerPos.getZ() + radius, 16);
        int worldMinY = client.level.getMinY();
        int worldMaxY = worldMinY + client.level.getHeight() - 1;
        int minY = Math.max(worldMinY, playerPos.getY() - radius);
        int maxY = Math.min(worldMaxY, playerPos.getY() + radius);
        double maxDistanceSq = range * range;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!client.level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                LevelChunk chunk = client.level.getChunk(chunkX, chunkZ);
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }
                LevelChunkSection[] sections = chunk.getSections();
                if (sections == null || sections.length == 0) {
                    continue;
                }
                int startX = chunkX << 4;
                int startZ = chunkZ << 4;
                int bottomSectionCoord = client.level.getMinSectionY();
                BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
                for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                    LevelChunkSection section = sections[sectionIndex];
                    if (section == null || section.hasOnlyAir()) {
                        continue;
                    }
                    if (!section.maybeHas(state -> !state.isAir() && owner.matchesAnyBlock(selections, state))) {
                        continue;
                    }

                    int sectionMinY = (bottomSectionCoord + sectionIndex) << 4;
                    int yStart = Math.max(minY, sectionMinY);
                    int yEnd = Math.min(maxY, sectionMinY + 15);
                    if (yStart > yEnd) {
                        continue;
                    }

                    for (int localX = 0; localX < 16; localX++) {
                        int worldX = startX + localX;
                        for (int localZ = 0; localZ < 16; localZ++) {
                            int worldZ = startZ + localZ;
                            for (int y = yStart; y <= yEnd; y++) {
                                int localY = y - sectionMinY;
                                BlockState state = section.getBlockState(localX, localY, localZ);
                                if (state.isAir() || !owner.matchesAnyBlock(selections, state)) {
                                    continue;
                                }
                                mutable.set(worldX, y, worldZ);
                                if (mutable.distSqr(playerPos) > maxDistanceSq) {
                                    continue;
                                }
                                matches.add(mutable.immutable());
                                if (resultLimit > 0 && matches.size() >= resultLimit) {
                                    matches.sort(Comparator.comparingDouble(pos -> pos.distSqr(playerPos)));
                                    return matches;
                                }
                            }
                        }
                    }
                }
            }
        }

        matches.sort(Comparator.comparingDouble(pos -> pos.distSqr(playerPos)));
        return matches;
    }

    Optional<BlockPos> findNearestAnyBlock(net.minecraft.client.Minecraft client, double range) {
        if (client == null || client.player == null || client.level == null) {
            return Optional.empty();
        }
        int radius = Math.max(1, Math.min((int) Math.ceil(range), 64));
        BlockPos playerPos = client.player.blockPosition();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;
        double maxDistanceSq = range * range;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double offsetDistanceSq = dx * dx + dy * dy + dz * dz;
                    if (offsetDistanceSq > maxDistanceSq) {
                        continue;
                    }
                    mutable.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    if (!client.level.hasChunk(Math.floorDiv(mutable.getX(), 16), Math.floorDiv(mutable.getZ(), 16))) {
                        continue;
                    }
                    BlockState state = client.level.getBlockState(mutable);
                    if (state.isAir()) {
                        continue;
                    }
                    double distance = mutable.distSqr(playerPos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPos = mutable.immutable();
                    }
                }
            }
        }

        return Optional.ofNullable(bestPos);
    }

    Optional<BlockPos> findNearestOpenBlock(net.minecraft.client.Minecraft client, int range) {
        if (client == null || client.player == null || client.level == null) {
            return Optional.empty();
        }
        int radius = Math.max(1, Math.min(range, 32));
        BlockPos playerPos = client.player.blockPosition();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    if (!client.level.hasChunk(Math.floorDiv(mutable.getX(), 16), Math.floorDiv(mutable.getZ(), 16))) {
                        continue;
                    }
                    if (!client.level.getWorldBorder().isWithinBounds(mutable)) {
                        continue;
                    }
                    if (!owner.isBlockReplaceable(client.level, mutable)) {
                        continue;
                    }
                    if (!owner.hasPlacementSupport(client.level, mutable)) {
                        continue;
                    }
                    AABB blockBox = new AABB(mutable.getX(), mutable.getY(), mutable.getZ(), mutable.getX() + 1, mutable.getY() + 1, mutable.getZ() + 1);
                    if (!client.level.getEntities(null, blockBox).isEmpty()) {
                        continue;
                    }
                    double distance = mutable.distSqr(playerPos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPos = mutable.immutable();
                    }
                }
            }
        }

        return Optional.ofNullable(bestPos);
    }
}
