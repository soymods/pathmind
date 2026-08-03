package com.pathmind.nodes;

import com.pathmind.execution.ExecutionManager;
import com.pathmind.util.BlockSelection;
import com.pathmind.util.EntityStateOptions;
import com.pathmind.util.GameProfileCompatibilityBridge;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class NodeWorldTargetResolver {
    private static final Pattern UNSAFE_RESOURCE_ID_PATTERN = Pattern.compile("[^a-z0-9_:/.-]");
    private static final Method CLIENT_WORLD_GET_ENTITY_BY_UUID = resolveClientWorldGetEntityByUuid();

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

    String getBlockParameterValue(Node node) {
        if (node == null) {
            return null;
        }
        String blockId = Node.getParameterString(node, "Block");
        if (blockId == null || blockId.isEmpty() || isAnySelectionValue(blockId)) {
            return null;
        }
        String state = Node.getParameterString(node, "State");
        if (state == null || state.isEmpty() || isAnySelectionValue(state)) {
            return blockId;
        }
        Optional<String> combined = BlockSelection.combine(blockId, state);
        if (combined.isPresent()) {
            return combined.get();
        }
        owner.notifyInvalidBlockStateSelection(blockId, state);
        return null;
    }

    String getEntityParameterState(Node node) {
        if (node == null) {
            return "";
        }
        String state = Node.getParameterString(node, "State");
        if (state == null) {
            return "";
        }
        String trimmedState = state.trim();
        if (trimmedState.isEmpty()) {
            return "";
        }
        String entityRaw = Node.getParameterString(node, "Entity");
        if (entityRaw == null || entityRaw.trim().isEmpty()) {
            return "";
        }
        String primaryEntity = entityRaw;
        List<String> parts = splitMultiValueList(entityRaw);
        if (!parts.isEmpty()) {
            primaryEntity = parts.getFirst();
        }
        String sanitized = sanitizeResourceId(primaryEntity);
        String normalized = sanitized != null && !sanitized.isEmpty()
            ? normalizeResourceId(sanitized, "minecraft")
            : primaryEntity;
        Identifier identifier = Identifier.tryParse(normalized);
        if (identifier == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
            return trimmedState;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (!EntityStateOptions.isStateSupported(BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).orElse(null), client != null ? client.level : null, trimmedState)) {
            owner.notifyInvalidEntityStateSelection(primaryEntity, trimmedState);
            return trimmedState;
        }
        return trimmedState;
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

    Optional<BlockPos> findNearestDroppedItem(net.minecraft.client.Minecraft client, Item item, double range) {
        return findNearestDroppedItemEntity(client, item, range).map(ItemEntity::blockPosition);
    }

    Optional<ItemEntity> findNearestDroppedItemEntity(net.minecraft.client.Minecraft client, Item item, double range) {
        if (client == null || client.player == null || client.level == null || item == null) {
            return Optional.empty();
        }
        double searchRadius = Math.max(1.0, range);
        AABB searchBox = client.player.getBoundingBox().inflate(searchRadius);
        List<ItemEntity> entities = client.level.getEntitiesOfClass(ItemEntity.class, searchBox,
            entity -> entity != null && !entity.isRemoved() && !entity.getItem().isEmpty() && entity.getItem().is(item));
        if (entities.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Collections.min(entities, Comparator.comparingDouble(entity -> entity.distanceToSqr(client.player))));
    }

    Optional<Entity> findNearestEntity(net.minecraft.client.Minecraft client, EntityType<?> entityType, double range) {
        return findNearestEntity(client, entityType, range, "");
    }

    Optional<Entity> findNearestEntity(net.minecraft.client.Minecraft client, EntityType<?> entityType, double range, String state) {
        if (client == null || client.player == null || client.level == null || entityType == null) {
            return Optional.empty();
        }
        double searchRadius = Math.max(1.0, range);
        AABB searchBox = client.player.getBoundingBox().inflate(searchRadius);
        Identifier targetTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        List<Entity> matches = client.level.getEntities(
            client.player,
            searchBox,
            entity -> {
                if (entity == null) {
                    return false;
                }
                EntityType<?> candidateType = entity.getType();
                boolean sameType = candidateType == entityType;
                if (!sameType) {
                    Identifier candidateId = BuiltInRegistries.ENTITY_TYPE.getKey(candidateType);
                    sameType = targetTypeId.equals(candidateId);
                }
                return sameType && EntityStateOptions.matchesState(entity, state);
            }
        );
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        for (Entity match : matches) {
            TransientEntityPositionTracker.remember(match);
        }
        Entity nearest = Collections.min(matches, Comparator.comparingDouble(entity -> entity.distanceToSqr(client.player)));
        return Optional.of(nearest);
    }

    Entity resolveEntityByUuid(net.minecraft.client.Minecraft client, java.util.UUID uuid) {
        if (client == null || client.level == null || uuid == null) {
            return null;
        }
        if (CLIENT_WORLD_GET_ENTITY_BY_UUID != null) {
            try {
                Object result = CLIENT_WORLD_GET_ENTITY_BY_UUID.invoke(client.level, uuid);
                if (result instanceof Entity entity) {
                    return entity;
                }
            } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException ignored) {
                // fall through to manual search
            }
        }

        if (client.player != null && uuid.equals(client.player.getUUID())) {
            return client.player;
        }
        for (AbstractClientPlayer player : client.level.players()) {
            if (player != null && uuid.equals(player.getUUID())) {
                return player;
            }
        }

        double searchRadius = 96.0;
        if (client.options != null) {
            int viewDistance = client.options.renderDistance().get();
            searchRadius = Math.max(searchRadius, viewDistance * 16.0);
        }
        AABB searchBox = client.player != null
            ? client.player.getBoundingBox().inflate(searchRadius)
            : new AABB(-searchRadius, -searchRadius, -searchRadius, searchRadius, searchRadius, searchRadius);
        List<Entity> matches = client.level.getEntities(
            client.player,
            searchBox,
            entity -> entity != null && uuid.equals(entity.getUUID())
        );
        return matches.isEmpty() ? null : matches.getFirst();
    }

    List<Entity> findEntitiesByType(net.minecraft.client.Minecraft client, EntityType<?> entityType, double range, String state) {
        if (client == null || client.player == null || client.level == null || entityType == null) {
            return Collections.emptyList();
        }
        double searchRadius = Math.max(1.0, range);
        AABB searchBox = client.player.getBoundingBox().inflate(searchRadius);
        return client.level.getEntities(
            client.player,
            searchBox,
            entity -> entity.getType() == entityType && EntityStateOptions.matchesState(entity, state)
        );
    }

    List<ItemEntity> findItemsByType(net.minecraft.client.Minecraft client, Item item, double range) {
        if (client == null || client.player == null || client.level == null || item == null) {
            return Collections.emptyList();
        }
        double searchRadius = Math.max(1.0, range);
        AABB searchBox = client.player.getBoundingBox().inflate(searchRadius);
        return client.level.getEntitiesOfClass(
            ItemEntity.class,
            searchBox,
            entity -> entity != null
                && !entity.isRemoved()
                && !entity.getItem().isEmpty()
                && entity.getItem().is(item)
        );
    }

    Entity resolveListItemEntity(Node listNode, RuntimeParameterData data, CompletableFuture<Void> future) {
        if (listNode == null) {
            return null;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) {
            return null;
        }

        String listName = Node.getParameterString(listNode, "List");
        if (listName == null || listName.trim().isEmpty()) {
            owner.sendNodeErrorMessage(client, Node.tr("pathmind.error.listNameEmpty"));
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return null;
        }

        ExecutionManager.RuntimeList list = resolveRuntimeList(listNode);
        if (list == null || list.getEntries().isEmpty()) {
            owner.sendNodeErrorMessage(client, Node.tr("pathmind.error.listEmptyOrMissing", listName.trim()));
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return null;
        }

        int index = Node.parseNodeInt(listNode, "Index", 1);
        if (index <= 0) {
            owner.sendNodeErrorMessage(client, Node.tr("pathmind.error.listIndexPositive"));
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return null;
        }

        int listIndex = index - 1;
        if (listIndex >= list.getEntries().size()) {
            owner.sendNodeErrorMessage(client, Node.tr("pathmind.error.listNoItem", listName.trim(), index));
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return null;
        }

        String entry = list.getEntries().get(listIndex);
        if (entry == null || entry.isEmpty()) {
            owner.sendNodeErrorMessage(client, Node.tr("pathmind.error.listNoItem", listName.trim(), index));
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return null;
        }

        if (entry.startsWith(Node.LIST_ENTRY_SERIALIZED_PREFIX)) {
            Node snapshot = owner.resolveListItemValueNode(listNode, future, true, data);
            if (snapshot == null) {
                return null;
            }
            NodeType snapshotType = snapshot.getType();
            if (snapshotType != NodeType.PARAM_ENTITY
                && snapshotType != NodeType.PARAM_PLAYER
                && snapshotType != NodeType.PARAM_ITEM) {
                return null;
            }
            RuntimeParameterData resolvedData = data != null ? data : new RuntimeParameterData();
            Optional<Vec3> resolved = owner.resolvePositionTarget(snapshot, resolvedData, future);
            if (resolved.isEmpty()) {
                return null;
            }
            return resolvedData.targetEntity;
        }

        if (list.getElementType() == NodeType.PARAM_GUI) {
            if (owner.getParameter("Slot") == null && owner.getParameter("SourceSlot") == null && owner.getParameter("TargetSlot") == null) {
                owner.sendNodeErrorMessage(client, Node.tr("pathmind.error.listGuiSlotsUnsupported", listName.trim(), owner.getType().getDisplayName()));
                if (future != null && !future.isDone()) {
                    future.complete(null);
                }
                return null;
            }
            ListSlotEntry slotEntry = parseListSlotEntry(entry);
            if (slotEntry == null) {
                owner.sendNodeErrorMessage(client, Node.tr("pathmind.error.listItemInvalidGuiSlot", listName.trim(), index));
                if (future != null && !future.isDone()) {
                    future.complete(null);
                }
                return null;
            }
            if (data != null) {
                data.slotIndex = slotEntry.slotIndex;
                data.slotSelectionType = slotEntry.selectionType;
            }
            applyListSlotSelection(slotEntry.slotIndex, listNode.getParentParameterSlotIndex());
            return client.player;
        }

        try {
            java.util.UUID uuid = java.util.UUID.fromString(entry);
            Entity entity = resolveEntityByUuid(client, uuid);
            if (entity == null || entity.isRemoved()) {
                owner.sendNodeErrorMessage(client, Node.tr("pathmind.error.listItemUnavailable", listName.trim(), index));
                if (future != null && !future.isDone()) {
                    future.complete(null);
                }
                return null;
            }
            if (data != null) {
                data.targetEntity = entity;
                Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
							  data.targetEntityId = entityId.toString();
						}

            NodeType elementType = list.getElementType();
            if (elementType == NodeType.PARAM_ITEM && entity instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getItem();
                if (stack != null && !stack.isEmpty()) {
                    Item item = stack.getItem();
                    Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
									  if (data != null) {
										    data.targetItem = item;
										    data.targetItemId = itemId.toString();
									  }
									owner.setParameterValueAndPropagate("Item", itemId.toString());
								}
            } else if (elementType == NodeType.PARAM_PLAYER && entity instanceof AbstractClientPlayer player) {
                String name = GameProfileCompatibilityBridge.getName(player.getGameProfile());
                if (name != null && !name.trim().isEmpty()) {
                    owner.setParameterValueAndPropagate("Player", name);
                }
            } else if (elementType == NodeType.PARAM_ENTITY) {
                Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
							  owner.setParameterValueAndPropagate("Entity", typeId.toString());
						}

            return entity;
        } catch (IllegalArgumentException ex) {
            NodeType elementType = list.getElementType();
            String trimmedEntry = entry.trim();

            if (elementType == NodeType.PARAM_ENTITY) {
                Identifier identifier = Identifier.tryParse(trimmedEntry);
                if (identifier != null && BuiltInRegistries.ENTITY_TYPE.containsKey(identifier)) {
                    EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).orElse(null);
                    Optional<Entity> nearest = findNearestEntity(client, entityType, Node.PARAMETER_SEARCH_RADIUS, "");
                    if (nearest.isPresent()) {
                        Entity entity = nearest.get();
                        if (data != null) {
                            data.targetEntity = entity;
                            data.targetEntityId = identifier.toString();
                        }
                        owner.setParameterValueAndPropagate("Entity", identifier.toString());
                        return entity;
                    }
                }
            }

            owner.sendNodeErrorMessage(client, Node.tr("pathmind.error.listItemUnavailable", listName.trim(), index));
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return null;
        }
    }

    ExecutionManager.RuntimeList resolveRuntimeList(Node listNode) {
        if (listNode == null) {
            return null;
        }
        String listName = Node.getParameterString(listNode, "List");
        if (listName == null || listName.trim().isEmpty()) {
            return null;
        }
        ExecutionManager manager = ExecutionManager.getInstance();
        Node startNode = owner.resolveExecutionStartNode();
        RuntimeValueScope scope = manager.resolveRuntimeListScope(
            startNode, listName.trim(), listNode.getRuntimeValueScope());
        return manager.getRuntimeList(startNode, listName.trim(), scope);
    }

    Optional<Integer> resolveListLengthValue(Node listNode) {
        if (listNode == null) {
            return Optional.empty();
        }
        String listName = Node.getParameterString(listNode, "List");
        if (listName == null || listName.trim().isEmpty()) {
            return Optional.empty();
        }
        ExecutionManager.RuntimeList list = resolveRuntimeList(listNode);
        if (list == null) {
            return Optional.of(0);
        }
        return Optional.of(list.getEntries().size());
    }

    ListSlotEntry resolveListItemSlotEntry(Node listNode, boolean reportErrors, CompletableFuture<Void> future) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (listNode == null) {
            return null;
        }
        ExecutionManager.RuntimeList list = resolveRuntimeList(listNode);
        String listName = Node.getParameterString(listNode, "List");
        String safeListName = listName == null ? "" : listName.trim();
        if (list == null || list.getEntries().isEmpty() || list.getElementType() != NodeType.PARAM_GUI) {
            return null;
        }

        int index = Node.parseNodeInt(listNode, "Index", 1);
        if (index <= 0 || index > list.getEntries().size()) {
            if (reportErrors && client != null) {
                owner.sendNodeErrorMessage(client, Node.tr("pathmind.error.listNoItem", safeListName, index));
            }
            if (reportErrors && future != null && !future.isDone()) {
                future.complete(null);
            }
            return null;
        }
        String entry = list.getEntries().get(index - 1);
        ListSlotEntry parsed = parseListSlotEntry(entry);
        if (parsed == null) {
            if (reportErrors && client != null) {
                owner.sendNodeErrorMessage(client, Node.tr("pathmind.error.listItemInvalidGuiSlot", safeListName, index));
            }
            if (reportErrors && future != null && !future.isDone()) {
                future.complete(null);
            }
        }
        return parsed;
    }

    ListSlotEntry parseListSlotEntry(String entry) {
        if (entry == null) {
            return null;
        }
        String trimmed = entry.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith(Node.LIST_SLOT_GUI_PREFIX)) {
            Integer slotIndex = Node.parseIntOrNull(trimmed.substring(Node.LIST_SLOT_GUI_PREFIX.length()));
            return slotIndex == null ? null : new ListSlotEntry(slotIndex, SlotSelectionType.GUI_CONTAINER);
        }
        if (trimmed.startsWith(Node.LIST_SLOT_PLAYER_PREFIX)) {
            Integer slotIndex = Node.parseIntOrNull(trimmed.substring(Node.LIST_SLOT_PLAYER_PREFIX.length()));
            return slotIndex == null ? null : new ListSlotEntry(slotIndex, SlotSelectionType.PLAYER_INVENTORY);
        }
        return null;
    }

    private void applyListSlotSelection(int slotIndex, int parameterSlotIndex) {
        if (owner.getParameter("Slot") != null) {
            owner.setParameterValueAndPropagate("Slot", Integer.toString(slotIndex));
            return;
        }
        if (owner.getParameter("SourceSlot") != null && (parameterSlotIndex <= 0 || owner.getParameter("TargetSlot") == null)) {
            owner.setParameterValueAndPropagate("SourceSlot", Integer.toString(slotIndex));
        }
        if (owner.getParameter("TargetSlot") != null && parameterSlotIndex == 1) {
            owner.setParameterValueAndPropagate("TargetSlot", Integer.toString(slotIndex));
        }
    }

    private static Method resolveClientWorldGetEntityByUuid() {
        try {
            Method method = net.minecraft.client.multiplayer.ClientLevel.class.getMethod("getEntity", java.util.UUID.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
