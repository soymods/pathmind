package com.pathmind.nodes;

import static com.pathmind.util.PathmindI18n.tr;

import com.pathmind.execution.ExecutionManager;
import com.pathmind.util.BlockSelection;
import com.pathmind.util.EntityStateOptions;
import com.pathmind.util.GameProfileCompatibilityBridge;
import com.pathmind.util.GuiSelectionMode;
import com.pathmind.util.InventorySlotModeHelper;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class NodeVariableListCommandExecutor {
    private final Node owner;

    NodeVariableListCommandExecutor(Node owner) {
        this.owner = owner;
    }
    void executeSetVariableCommand(CompletableFuture<Void> future) {
        Node slot0 = owner.getAttachedParameter(0);
        Node slot1 = owner.getAttachedParameter(1);
        Node variableNode = slot0;
        Node valueNode = slot1;
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (variableNode == null || variableNode.getType() != NodeType.VARIABLE
            || valueNode == null || valueNode.getType() == NodeType.VARIABLE) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.setVariableRequiresInputs"));
            return;
        }

        String variableName = Node.getParameterString(variableNode, "Variable");
        if (variableName == null || variableName.trim().isEmpty()) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.variableNameEmpty"));
            return;
        }

        ExecutionManager manager = ExecutionManager.getInstance();
        Node startNode = owner.getOwningStartNode();
        if (startNode == null && owner.getParentControl() != null) {
            startNode = owner.getParentControl().getOwningStartNode();
        }
        NodeType valueType = valueNode.getType();
        Map<String, String> values;
        if (valueNode.isSensorNode() && NodeCatalog.isBooleanSensor(valueType)) {
            boolean sensorResult = valueNode.evaluateSensor();
            values = new HashMap<>();
            values.put("Toggle", Boolean.toString(sensorResult));
            values.put(Node.normalizeParameterKey("Toggle"), Boolean.toString(sensorResult));
            valueType = NodeType.PARAM_BOOLEAN;
        } else
        if (valueType == NodeType.SENSOR_POSITION_OF) {
            Node parameterNode = valueNode.getAttachedParameter(0);
            if (parameterNode == null) {
                NodeExecutionCompletion.fail(owner, client, future,
                    tr("pathmind.error.positionOfRequiresParameter"));
                return;
            }
            Optional<Vec3d> resolved = valueNode.resolvePositionTarget(parameterNode, null, null);
            if (resolved.isEmpty()) {
                owner.setNextOutputSocket(Node.NO_OUTPUT);
                NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.positionOfTargetUnresolved"));
                return;
            }
            values = valueNode.exportParameterValues();
            valueType = valueNode.getResolvedValueType();
        } else if (valueType == NodeType.SENSOR_DISTANCE_BETWEEN) {
            Node parameterNodeA = valueNode.getAttachedParameter(0);
            Node parameterNodeB = valueNode.getAttachedParameter(1);
            if (parameterNodeA == null || parameterNodeB == null) {
                owner.setNextOutputSocket(Node.NO_OUTPUT);
                NodeExecutionCompletion.fail(owner, client, future,
                    tr("pathmind.error.distanceBetweenRequiresTwoParameters"));
                return;
            }
            if ((!valueNode.providesTrait(parameterNodeA, NodeValueTrait.ENTITY)
                && !valueNode.providesTrait(parameterNodeA, NodeValueTrait.COORDINATE)
                && !valueNode.providesTrait(parameterNodeA, NodeValueTrait.BLOCK)
                && !valueNode.providesTrait(parameterNodeA, NodeValueTrait.ITEM)
                && !valueNode.providesTrait(parameterNodeA, NodeValueTrait.PLAYER))
                || (!valueNode.providesTrait(parameterNodeB, NodeValueTrait.ENTITY)
                && !valueNode.providesTrait(parameterNodeB, NodeValueTrait.COORDINATE)
                && !valueNode.providesTrait(parameterNodeB, NodeValueTrait.BLOCK)
                && !valueNode.providesTrait(parameterNodeB, NodeValueTrait.ITEM)
                && !valueNode.providesTrait(parameterNodeB, NodeValueTrait.PLAYER))) {
                owner.setNextOutputSocket(Node.NO_OUTPUT);
                NodeExecutionCompletion.fail(owner, client, future,
                    tr("pathmind.error.distanceBetweenInvalidParameters"));
                return;
            }
            Optional<Vec3d> resolvedA = valueNode.resolveDistanceBetweenTarget(parameterNodeA);
            Optional<Vec3d> resolvedB = valueNode.resolveDistanceBetweenTarget(parameterNodeB);
            if (resolvedA.isEmpty() || resolvedB.isEmpty()) {
                owner.setNextOutputSocket(Node.NO_OUTPUT);
                NodeExecutionCompletion.fail(owner, client, future,
                    tr("pathmind.error.distanceBetweenTargetsUnresolved"));
                return;
            }
            double distance = Math.sqrt(resolvedA.get().squaredDistanceTo(resolvedB.get()));
            values = new HashMap<>();
            values.put("Distance", Double.toString(distance));
            valueType = NodeType.PARAM_DISTANCE;
        } else {
            values = exportResolvedParameterValues(valueNode);
            NodeType resolvedValueType = valueNode.getResolvedValueType();
            if (resolvedValueType != valueNode.getType()) {
                valueType = resolvedValueType;
            }
        }
        ExecutionManager.RuntimeVariable value = new ExecutionManager.RuntimeVariable(valueType, values);
        boolean stored = startNode != null && manager.setRuntimeVariable(startNode, variableName.trim(), value);
        if (!stored) {
            manager.setRuntimeVariableForAnyActiveChain(variableName.trim(), value);
        }
        NodeExecutionCompletion.complete(future);
    }

    void executeChangeVariableCommand(CompletableFuture<Void> future) {
        Node variableNode = owner.getAttachedParameter(0);

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (variableNode == null) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.changeVariableRequiresVariable"));
            return;
        }

        String variableName = Node.getParameterString(variableNode, "Variable");
        if (variableName == null || variableName.trim().isEmpty()) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.variableNameEmpty"));
            return;
        }

        ExecutionManager manager = ExecutionManager.getInstance();
        Node startNode = owner.getOwningStartNode();
        if (startNode == null) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.noActiveTreeVariableChange"));
            return;
        }

        ExecutionManager.RuntimeVariable current = manager.getRuntimeVariable(startNode, variableName.trim());
        if (current == null) {
            NodeExecutionCompletion.fail(owner, client, future,
                tr("pathmind.error.variableNotSet", variableName.trim()));
            return;
        }

        NodeType valueType = current.getType();
        if (valueType == null) {
            NodeExecutionCompletion.fail(owner, client, future,
                tr("pathmind.error.variableNoValue", variableName.trim()));
            return;
        }

        Node snapshot = new Node(valueType, 0, 0);
        snapshot.setSocketsHidden(true);
        Map<String, String> values = current.getValues();
        if (values != null && !values.isEmpty()) {
            snapshot.applyParameterValuesFromMap(values);
        }

        double amount = owner.getDoubleParameter("Amount", 1.0);
        String operation = owner.getAmountOperation();
        if ((operation.equals("/") || operation.equals("%")) && Math.abs(amount) < 1.0E-9) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.changeVariableDivideByZero"));
            return;
        }

        String[] error = new String[1];
        if (!applyNumericOperation(snapshot, amount, operation, error)) {
            NodeExecutionCompletion.fail(owner, client, future, error[0] != null ? error[0]
                : tr("pathmind.error.changeVariableRequiresSingleNumericValue"));
            return;
        }

        Map<String, String> updatedValues = snapshot.exportParameterValues();
        ExecutionManager.RuntimeVariable updated = new ExecutionManager.RuntimeVariable(valueType, updatedValues);
        manager.setRuntimeVariable(startNode, variableName.trim(), updated);
        NodeExecutionCompletion.complete(future);
    }

    enum RemoveListMode {
        FIRST,
        LAST,
        INDEX,
        VALUE
    }

    void executeAddToListCommand(CompletableFuture<Void> future) {
        Node parameterNode = owner.getAttachedParameter(0);
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (parameterNode == null) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.addToListRequiresParameter"));
            return;
        }

        String listName = owner.getStringParameter("List", "");
        if (listName == null || listName.trim().isEmpty()) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.listNameEmpty"));
            return;
        }

        Node.ListValueEntry listValue = resolveListValueEntry(parameterNode, future);
        if (future.isDone()) {
            return;
        }
        if (listValue == null || listValue.entry == null || listValue.entry.trim().isEmpty()) {
            NodeExecutionCompletion.fail(owner, client, future,
                tr("pathmind.error.noMatchingTargetNearby", owner.getType().getDisplayName()));
            return;
        }

        Node startNode = owner.resolveExecutionStartNode();
        if (startNode == null) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.noActiveTreeListUpdate"));
            return;
        }

        ExecutionManager manager = ExecutionManager.getInstance();
        ExecutionManager.RuntimeList runtimeList = manager.getRuntimeList(startNode, listName.trim());
        if (runtimeList == null) {
            runtimeList = new ExecutionManager.RuntimeList(listValue.elementType, Collections.singletonList(listValue.entry));
            manager.setRuntimeList(startNode, listName.trim(), runtimeList);
            NodeExecutionCompletion.complete(future);
            return;
        }

        if (runtimeList.getElementType() != listValue.elementType) {
            NodeExecutionCompletion.fail(owner, client, future,
                "List \"" + listName.trim() + "\" stores " + describeListElementType(runtimeList.getElementType())
                    + " entries and cannot accept " + describeListElementType(listValue.elementType) + ".");
            return;
        }

        runtimeList.addEntry(listValue.entry);
        NodeExecutionCompletion.complete(future);
    }

    void executeRemoveFromListCommand(CompletableFuture<Void> future, RemoveListMode mode) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        String listName = owner.getStringParameter("List", "");
        if (listName == null || listName.trim().isEmpty()) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.listNameEmpty"));
            return;
        }

        ExecutionManager.RuntimeList runtimeList = owner.resolveRuntimeList(owner);
        if (runtimeList == null || runtimeList.isEmpty()) {
            NodeExecutionCompletion.fail(owner, client, future,
                tr("pathmind.error.listEmptyOrMissing", listName.trim()));
            return;
        }

        String removed = null;
        if (mode == RemoveListMode.FIRST) {
            removed = runtimeList.removeFirstEntry();
        } else if (mode == RemoveListMode.LAST) {
            removed = runtimeList.removeLastEntry();
        } else if (mode == RemoveListMode.INDEX) {
            int index = owner.getIntParameter("Index", 1);
            if (index <= 0) {
                NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.listIndexPositive"));
                return;
            }
            removed = runtimeList.removeEntry(index - 1);
            if (removed == null) {
                NodeExecutionCompletion.fail(owner, client, future,
                    tr("pathmind.error.listNoItem", listName.trim(), index));
                return;
            }
        } else {
            Node valueNode = owner.getAttachedParameter(0);
            if (valueNode == null) {
                NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.removeFromListRequiresValue"));
                return;
            }
            int removedCount = 0;
            while (true) {
                int matchedIndex = findListEntryIndexByValue(runtimeList, valueNode, future);
                if (future.isDone()) {
                    return;
                }
                if (matchedIndex < 0) {
                    break;
                }
                removed = runtimeList.removeEntry(matchedIndex);
                if (removed == null) {
                    break;
                }
                removedCount++;
            }
            if (removedCount <= 0) {
                NodeExecutionCompletion.fail(owner, client, future,
                    tr("pathmind.error.noMatchingEntryInList", listName.trim()));
                return;
            }
        }

        if (removed == null) {
            NodeExecutionCompletion.fail(owner, client, future,
                tr("pathmind.error.listEmptyOrMissing", listName.trim()));
            return;
        }
        NodeExecutionCompletion.complete(future);
    }

    void executeCreateListCommand(CompletableFuture<Void> future) {
        Node parameterNode = owner.getAttachedParameter(0);
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (parameterNode == null) {
            NodeExecutionCompletion.fail(owner, client, future,
                tr("pathmind.error.createListRequiresParameter"));
            return;
        }

        parameterNode = resolveCreateListTargetParameter(parameterNode, future);
        if (future.isDone()) {
            return;
        }
        if (parameterNode == null) {
            NodeExecutionCompletion.fail(owner, client, future,
                tr("pathmind.error.createListValueUnresolved"));
            return;
        }

        NodeType parameterType = parameterNode.getType();
        String listName = owner.getStringParameter("List", "");
        if (listName == null || listName.trim().isEmpty()) {
            NodeExecutionCompletion.fail(owner, client, future, tr("pathmind.error.listNameEmpty"));
            return;
        }

        if (client == null || client.player == null || client.world == null) {
            NodeExecutionCompletion.complete(future);
            return;
        }

        owner.ensureCreateListRadiusParameters();
        boolean useCustomRadius = isCreateListCustomRadiusEnabled();
        double searchRadius = getCreateListSearchRadius(client);

        if (!isCreateListCollectionTarget(parameterType)) {
            Node.ListValueEntry singleValue = resolveListValueEntry(parameterNode, future);
            if (future.isDone()) {
                return;
            }
            if (singleValue == null || singleValue.entry == null || singleValue.entry.trim().isEmpty()) {
                NodeExecutionCompletion.fail(owner, client, future,
                    tr("pathmind.error.createListValueUnresolved"));
                return;
            }

            ExecutionManager manager = ExecutionManager.getInstance();
            Node startNode = owner.resolveExecutionStartNode();
            if (startNode == null) {
                NodeExecutionCompletion.fail(owner, client, future,
                    tr("pathmind.error.noActiveTreeListCreation"));
                return;
            }
            manager.setRuntimeList(startNode, listName.trim(),
                new ExecutionManager.RuntimeList(singleValue.elementType, Collections.singletonList(singleValue.entry)));
            NodeExecutionCompletion.complete(future);
            return;
        }

        List<Entity> matches = new ArrayList<>();
        if (parameterType == NodeType.PARAM_BLOCK) {
            List<BlockSelection> blocks = owner.resolveBlocksFromParameter(parameterNode);
            if (blocks.isEmpty()) {
                NodeExecutionCompletion.fail(owner, client, future,
                    tr("pathmind.error.noBlockSelectedForNode", owner.getType().getDisplayName()));
                return;
            }

            List<BlockPos> positions = owner.findBlocksWithinRange(client, blocks, searchRadius);
            if (positions.isEmpty()) {
                NodeExecutionCompletion.fail(owner, client, future,
                    tr("pathmind.error.noMatchingBlocksNearby", owner.getType().getDisplayName()));
                return;
            }
            if (isCreateListBlockCapEnabled() && positions.size() > getCreateListMaxBlocks()) {
                positions = new ArrayList<>(positions.subList(0, getCreateListMaxBlocks()));
            }

            List<String> entries = new ArrayList<>();
            for (BlockPos pos : positions) {
                if (pos == null) {
                    continue;
                }
                Map<String, String> values = new HashMap<>();
                values.put("X", Integer.toString(pos.getX()));
                values.put("Y", Integer.toString(pos.getY()));
                values.put("Z", Integer.toString(pos.getZ()));
                values.put("x", Integer.toString(pos.getX()));
                values.put("y", Integer.toString(pos.getY()));
                values.put("z", Integer.toString(pos.getZ()));
                entries.add(serializeListEntryValues(values));
            }

            ExecutionManager manager = ExecutionManager.getInstance();
            Node startNode = owner.resolveExecutionStartNode();
            if (startNode == null) {
                NodeExecutionCompletion.fail(owner, client, future,
                    tr("pathmind.error.noActiveTreeListCreation"));
                return;
            }

            manager.setRuntimeList(startNode, listName.trim(),
                new ExecutionManager.RuntimeList(NodeType.PARAM_COORDINATE, entries));
            NodeExecutionCompletion.complete(future);
            return;
        } else if (parameterType == NodeType.PARAM_ENTITY) {
            String state = owner.getEntityParameterState(parameterNode);
            String rawEntity = Node.getParameterString(parameterNode, "Entity");
            if (Node.isAnySelectionValue(rawEntity)) {
                matches.addAll(useCustomRadius
                    ? findEntitiesWithinRange(client, searchRadius, state)
                    : findRenderedEntities(client, state));
            } else {
                List<String> entityIds = owner.resolveEntityIdsFromParameter(parameterNode);
                if (entityIds.isEmpty()) {
                    NodeExecutionCompletion.fail(owner, client, future,
                        "No entity selected for " + owner.getType().getDisplayName() + ".");
                    return;
                }

                for (String candidateId : entityIds) {
                    Identifier identifier = Identifier.tryParse(candidateId);
                    if (identifier == null || !Registries.ENTITY_TYPE.containsId(identifier)) {
                        continue;
                    }
                    EntityType<?> entityType = Registries.ENTITY_TYPE.get(identifier);
                    matches.addAll(useCustomRadius
                        ? findEntitiesByTypeWithinRange(client, entityType, searchRadius, state)
                        : findRenderedEntitiesByType(client, entityType, state));
                }
            }
        } else if (parameterType == NodeType.PARAM_PLAYER) {
            String playerName = Node.getParameterString(parameterNode, "Player");
            List<AbstractClientPlayerEntity> nearbyPlayers = useCustomRadius
                ? findPlayersWithinRange(client, searchRadius)
                : client.world.getPlayers();
            if (Node.isAnyPlayerValue(playerName)) {
                matches.addAll(nearbyPlayers);
            } else if (Node.isSelfPlayerValue(playerName)) {
                if (!useCustomRadius || client.player.squaredDistanceTo(client.player) <= searchRadius * searchRadius) {
                    matches.add(client.player);
                }
            } else {
                for (AbstractClientPlayerEntity player : nearbyPlayers) {
                    if (player == null) {
                        continue;
                    }
                    if (playerName != null && playerName.equalsIgnoreCase(
                        GameProfileCompatibilityBridge.getName(player.getGameProfile()))) {
                        matches.add(player);
                    }
                }
            }

            if (matches.isEmpty()) {
                String message;
                if (Node.isAnyPlayerValue(playerName)) {
                    message = "No players nearby for " + owner.getType().getDisplayName() + ".";
                } else if (Node.isSelfPlayerValue(playerName)) {
                    message = "Local player unavailable for " + owner.getType().getDisplayName() + ".";
                } else {
                    message = "Player \"" + playerName + "\" is not nearby for " + owner.getType().getDisplayName() + ".";
                }
                NodeExecutionCompletion.fail(owner, client, future, message);
                return;
            }
        } else if (parameterType == NodeType.PARAM_ITEM) {
            List<String> itemIds = owner.resolveItemIdsFromParameter(parameterNode);
            if (itemIds.isEmpty()) {
                NodeExecutionCompletion.fail(owner, client, future,
                    "No item selected for " + owner.getType().getDisplayName() + ".");
                return;
            }

            for (String candidateId : itemIds) {
                Identifier identifier = Identifier.tryParse(candidateId);
                if (identifier == null || !Registries.ITEM.containsId(identifier)) {
                    continue;
                }
                Item item = Registries.ITEM.get(identifier);
                matches.addAll(useCustomRadius
                    ? findItemsWithinRange(client, item, searchRadius)
                    : findRenderedItemsByType(client, item));
            }
        } else if (parameterType == NodeType.PARAM_GUI) {
            ScreenHandler handler = client.player.currentScreenHandler;
            if (handler == null) {
                NodeExecutionCompletion.fail(owner, client, future,
                    tr("pathmind.error.noGuiOpenForNode", owner.getType().getDisplayName()));
                return;
            }

            GuiSelectionMode guiMode = GuiSelectionMode.fromId(Node.getParameterString(parameterNode, "GUI"));
            List<String> entries = collectGuiListEntries(handler, guiMode);
            if (entries.isEmpty()) {
                NodeExecutionCompletion.fail(owner, client, future,
                    tr("pathmind.error.noMatchingGuiSlots", owner.getType().getDisplayName()));
                return;
            }

            ExecutionManager manager = ExecutionManager.getInstance();
            Node startNode = owner.resolveExecutionStartNode();
            if (startNode == null) {
                NodeExecutionCompletion.fail(owner, client, future,
                    tr("pathmind.error.noActiveTreeListCreation"));
                return;
            }

            manager.setRuntimeList(startNode, listName.trim(),
                new ExecutionManager.RuntimeList(parameterType, entries));
            NodeExecutionCompletion.complete(future);
            return;
        }

        if (matches.isEmpty()) {
            // Fallback for entity lists: keep configured entity IDs so downstream LIST_ITEM
            // can resolve nearest matching entities at use time.
            if (parameterType == NodeType.PARAM_ENTITY) {
                List<String> configuredEntityIds = owner.resolveEntityIdsFromParameter(parameterNode);
                if (!configuredEntityIds.isEmpty()) {
                    ExecutionManager manager = ExecutionManager.getInstance();
                    Node startNode = owner.resolveExecutionStartNode();
                    if (startNode != null) {
                        manager.setRuntimeList(startNode, listName.trim(),
                            new ExecutionManager.RuntimeList(parameterType, configuredEntityIds));
                        NodeExecutionCompletion.complete(future);
                        return;
                    }
                }
            }

            NodeExecutionCompletion.fail(owner, client, future,
                tr("pathmind.error.noMatchingTargetsNearby", owner.getType().getDisplayName()));
            return;
        }

        matches.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(client.player)));
        List<String> entries = new ArrayList<>();
        for (Entity entity : matches) {
            if (entity != null && !entity.isRemoved()) {
                entries.add(entity.getUuidAsString());
            }
        }

        ExecutionManager manager = ExecutionManager.getInstance();
        Node startNode = owner.resolveExecutionStartNode();
        if (startNode == null) {
            NodeExecutionCompletion.fail(owner, client, future,
                tr("pathmind.error.noActiveTreeListCreation"));
            return;
        }

        manager.setRuntimeList(startNode, listName.trim(),
            new ExecutionManager.RuntimeList(parameterType, entries));
        NodeExecutionCompletion.complete(future);
    }

    Node resolveCreateListTargetParameter(Node parameterNode, CompletableFuture<Void> future) {
        if (parameterNode != null && parameterNode.getType() == NodeType.VARIABLE) {
            return owner.resolveVariableValueNode(parameterNode, 0, future);
        }
        return parameterNode;
    }

    static boolean isCreateListCollectionTarget(NodeType parameterType) {
        return parameterType == NodeType.PARAM_BLOCK
            || parameterType == NodeType.PARAM_ENTITY
            || parameterType == NodeType.PARAM_PLAYER
            || parameterType == NodeType.PARAM_ITEM
            || parameterType == NodeType.PARAM_GUI;
    }

    private List<String> collectGuiListEntries(ScreenHandler handler, GuiSelectionMode guiMode) {
        if (handler == null) {
            return Collections.emptyList();
        }
        List<String> entries = new ArrayList<>();
        for (int slotIndex = 0; slotIndex < handler.slots.size(); slotIndex++) {
            Slot slot = handler.getSlot(slotIndex);
            if (slot == null) {
                continue;
            }
            boolean playerSlot = slot.inventory instanceof PlayerInventory;
            if (guiMode == GuiSelectionMode.PLAYER_INVENTORY && !playerSlot) {
                continue;
            }
            if (guiMode != null && guiMode != GuiSelectionMode.PLAYER_INVENTORY && playerSlot) {
                continue;
            }
            int storedSlotIndex = playerSlot ? slot.getIndex() : slotIndex;
            entries.add((playerSlot ? Node.LIST_SLOT_PLAYER_PREFIX : Node.LIST_SLOT_GUI_PREFIX) + storedSlotIndex);
        }
        return entries;
    }

    private List<Entity> findRenderedEntities(net.minecraft.client.MinecraftClient client, String state) {
        if (client == null || client.player == null || client.world == null) {
            return Collections.emptyList();
        }
        double renderDistance = getCurrentRenderDistanceBlocks(client);
        Box searchBox = client.player.getBoundingBox().expand(renderDistance);
        return client.world.getOtherEntities(
            client.player,
            searchBox,
            entity -> entity != null
                && entity.isAlive()
                && EntityStateOptions.matchesState(entity, state)
        );
    }

    private List<Entity> findEntitiesWithinRange(net.minecraft.client.MinecraftClient client, double range, String state) {
        if (client == null || client.player == null || client.world == null) {
            return Collections.emptyList();
        }
        double searchRadius = Math.max(1.0, range);
        Box searchBox = client.player.getBoundingBox().expand(searchRadius);
        return client.world.getOtherEntities(
            client.player,
            searchBox,
            entity -> entity != null
                && entity.isAlive()
                && EntityStateOptions.matchesState(entity, state)
        );
    }

    private List<Entity> findRenderedEntitiesByType(net.minecraft.client.MinecraftClient client, EntityType<?> entityType, String state) {
        if (client == null || client.player == null || client.world == null || entityType == null) {
            return Collections.emptyList();
        }
        double renderDistance = getCurrentRenderDistanceBlocks(client);
        Box searchBox = client.player.getBoundingBox().expand(renderDistance);
        return client.world.getOtherEntities(
            client.player,
            searchBox,
            entity -> entity != null
                && entity.isAlive()
                && entity.getType() == entityType
                && EntityStateOptions.matchesState(entity, state)
        );
    }

    private List<Entity> findEntitiesByTypeWithinRange(net.minecraft.client.MinecraftClient client, EntityType<?> entityType,
                                                       double range, String state) {
        if (client == null || client.player == null || client.world == null || entityType == null) {
            return Collections.emptyList();
        }
        double searchRadius = Math.max(1.0, range);
        Box searchBox = client.player.getBoundingBox().expand(searchRadius);
        return client.world.getOtherEntities(
            client.player,
            searchBox,
            entity -> entity != null
                && entity.isAlive()
                && entity.getType() == entityType
                && EntityStateOptions.matchesState(entity, state)
        );
    }

    private List<ItemEntity> findRenderedItemsByType(net.minecraft.client.MinecraftClient client, Item item) {
        if (client == null || client.player == null || client.world == null || item == null) {
            return Collections.emptyList();
        }
        double renderDistance = getCurrentRenderDistanceBlocks(client);
        Box searchBox = client.player.getBoundingBox().expand(renderDistance);
        return client.world.getEntitiesByClass(
            ItemEntity.class,
            searchBox,
            entity -> entity != null
                && !entity.isRemoved()
                && !entity.getStack().isEmpty()
                && entity.getStack().isOf(item)
        );
    }

    private List<ItemEntity> findItemsWithinRange(net.minecraft.client.MinecraftClient client, Item item, double range) {
        if (client == null || client.player == null || client.world == null || item == null) {
            return Collections.emptyList();
        }
        double searchRadius = Math.max(1.0, range);
        Box searchBox = client.player.getBoundingBox().expand(searchRadius);
        return client.world.getEntitiesByClass(
            ItemEntity.class,
            searchBox,
            entity -> entity != null
                && !entity.isRemoved()
                && !entity.getStack().isEmpty()
                && entity.getStack().isOf(item)
        );
    }

    private List<AbstractClientPlayerEntity> findPlayersWithinRange(net.minecraft.client.MinecraftClient client, double range) {
        if (client == null || client.player == null || client.world == null) {
            return Collections.emptyList();
        }
        double maxDistanceSquared = Math.max(1.0, range);
        maxDistanceSquared *= maxDistanceSquared;
        List<AbstractClientPlayerEntity> players = new ArrayList<>();
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player == null || player.isRemoved()) {
                continue;
            }
            if (player.squaredDistanceTo(client.player) <= maxDistanceSquared) {
                players.add(player);
            }
        }
        return players;
    }

    private double getCurrentRenderDistanceBlocks(net.minecraft.client.MinecraftClient client) {
        if (client == null || client.options == null) {
            return 16.0;
        }
        return Math.max(16.0, client.options.getViewDistance().getValue() * 16.0);
    }

    private boolean isCreateListCustomRadiusEnabled() {
        owner.ensureCreateListRadiusParameters();
        return owner.getBooleanParameter("UseRadius", false);
    }

    private boolean isCreateListBlockCapEnabled() {
        owner.ensureCreateListRadiusParameters();
        return owner.getBooleanParameter("UseBlockCap", false);
    }

    private double getCreateListSearchRadius(net.minecraft.client.MinecraftClient client) {
        owner.ensureCreateListRadiusParameters();
        if (!isCreateListCustomRadiusEnabled()) {
            return getCurrentRenderDistanceBlocks(client);
        }
        return Math.max(1.0, owner.getDoubleParameter("Radius", getCurrentRenderDistanceBlocks(client)));
    }

    private int getCreateListMaxBlocks() {
        owner.ensureCreateListRadiusParameters();
        return Math.max(1, owner.getIntParameter("MaxBlocks", 256));
    }

    private Node.ListValueEntry resolveListValueEntry(Node parameterNode, CompletableFuture<Void> future) {
        if (parameterNode == null) {
            return null;
        }
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return null;
        }

        if (parameterNode.getType() == NodeType.VARIABLE) {
            parameterNode = owner.resolveVariableValueNode(parameterNode, 0, future);
            if (parameterNode == null) {
                return null;
            }
        }

        NodeType parameterType = parameterNode.getType();
        NodeBehaviorDefinition behaviorDefinition = NodeBehaviorDefinitionRegistry.get(parameterType);
        if (behaviorDefinition != null && behaviorDefinition.hasListEntryBehavior()) {
            return behaviorDefinition.resolveListValueEntry(owner, parameterNode, client);
        }

        NodeType resolvedType = parameterNode.getResolvedValueType();
        Map<String, String> exported = exportResolvedParameterValues(parameterNode);
        if (resolvedType == null || exported == null || exported.isEmpty()) {
            if (client != null) {
                owner.sendNodeErrorMessage(client, tr("pathmind.error.noValueForList"));
            }
            if (future != null && !future.isDone()) {
                future.complete(null);
            }
            return null;
        }
        return new Node.ListValueEntry(resolvedType, serializeListEntryValues(exported));
    }

    private String describeListElementType(NodeType type) {
        if (type == NodeType.PARAM_COORDINATE) {
            return "coordinate";
        }
        if (type == NodeType.PARAM_ENTITY) {
            return "entity";
        }
        if (type == NodeType.PARAM_PLAYER) {
            return "player";
        }
        if (type == NodeType.PARAM_ITEM) {
            return "item";
        }
        if (type == NodeType.PARAM_GUI) {
            return "GUI";
        }
        return "unknown";
    }

    private String serializeListEntryValues(Map<String, String> values) {
        return Node.LIST_ENTRY_SERIALIZED_PREFIX + Node.LIST_ENTRY_GSON.toJson(values == null ? Collections.emptyMap() : values);
    }

    private Map<String, String> exportResolvedParameterValues(Node source) {
        if (source == null) {
            return Collections.emptyMap();
        }
        Map<String, String> values = source.exportParameterValues();
        if (values == null || values.isEmpty()) {
            return values;
        }
        for (NodeParameter parameter : source.getParameters()) {
            if (parameter == null) {
                continue;
            }
            String key = parameter.getName();
            if (key == null || key.isEmpty()) {
                continue;
            }
            String resolvedValue = Node.getParameterString(source, key);
            if (resolvedValue == null) {
                continue;
            }
            values.put(key, resolvedValue);
            values.put(Node.normalizeParameterKey(key), resolvedValue);
        }
        return values;
    }

    private Map<String, String> deserializeListEntryValues(String entry) {
        if (entry == null || !entry.startsWith(Node.LIST_ENTRY_SERIALIZED_PREFIX)) {
            return Collections.emptyMap();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> parsed = Node.LIST_ENTRY_GSON.fromJson(
                entry.substring(Node.LIST_ENTRY_SERIALIZED_PREFIX.length()), Map.class);
            return parsed == null ? Collections.emptyMap() : parsed;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private int findListEntryIndexByValue(ExecutionManager.RuntimeList list, Node valueNode, CompletableFuture<Void> future) {
        if (list == null || valueNode == null) {
            return -1;
        }
        Node comparisonNode = valueNode;
        if (comparisonNode.getType() == NodeType.VARIABLE) {
            comparisonNode = owner.resolveVariableValueNode(comparisonNode, 0, future);
            if (comparisonNode == null) {
                return -1;
            }
        }
        for (int i = 0; i < list.size(); i++) {
            Node entryNode = buildListEntrySnapshot(list, i + 1, false, null, future);
            if (entryNode == null) {
                continue;
            }
            Optional<Boolean> equals = owner.compareParameterNodes(entryNode, comparisonNode);
            if (equals.orElse(false)) {
                return i;
            }
        }
        return -1;
    }

    private Node buildListEntrySnapshot(ExecutionManager.RuntimeList list, int index, boolean reportErrors,
                                        RuntimeParameterData data, CompletableFuture<Void> future) {
        if (list == null || index <= 0 || index > list.size()) {
            return null;
        }
        String entry = list.getEntry(index - 1);
        if (entry == null || entry.isEmpty()) {
            return null;
        }

        if (entry.startsWith(Node.LIST_ENTRY_SERIALIZED_PREFIX)) {
            NodeType elementType = list.getElementType();
            if (elementType == null) {
                return null;
            }
            Map<String, String> values = deserializeListEntryValues(entry);
            if (values.isEmpty()) {
                return null;
            }
            Node snapshot = new Node(elementType, 0, 0);
            snapshot.setSocketsHidden(true);
            snapshot.applyParameterValuesFromMap(values);
            if (data != null) {
                String x = values.get("X");
                String y = values.get("Y");
                String z = values.get("Z");
                Integer xi = Node.parseIntOrNull(x);
                Integer yi = Node.parseIntOrNull(y);
                Integer zi = Node.parseIntOrNull(z);
                if (xi != null && yi != null && zi != null) {
                    data.targetBlockPos = new BlockPos(xi, yi, zi);
                    data.targetVector = Vec3d.ofCenter(data.targetBlockPos);
                }
            }
            return snapshot;
        }

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        NodeType elementType = list.getElementType();
        if (elementType == NodeType.PARAM_ENTITY || elementType == NodeType.PARAM_PLAYER || elementType == NodeType.PARAM_ITEM) {
            Node snapshot = new Node(elementType, 0, 0);
            snapshot.setSocketsHidden(true);
            try {
                Entity resolved = null;
                try {
                    java.util.UUID uuid = java.util.UUID.fromString(entry);
                    if (client != null) {
                        resolved = owner.resolveEntityByUuid(client, uuid);
                    }
                } catch (IllegalArgumentException ignored) {
                }

                if (elementType == NodeType.PARAM_ENTITY) {
                    if (resolved != null) {
                        Identifier typeId = Registries.ENTITY_TYPE.getId(resolved.getType());
                        if (typeId != null) {
                            snapshot.setParameterValueAndPropagate("Entity", typeId.toString());
                        }
                        String state = EntityStateOptions.describe(resolved);
                        if (state != null) {
                            snapshot.setParameterValueAndPropagate("State", state);
                        }
                        if (data != null) {
                            data.targetEntity = resolved;
                            data.targetBlockPos = resolved.getBlockPos();
                        }
                    } else {
                        snapshot.setParameterValueAndPropagate("Entity", entry);
                    }
                    return snapshot;
                }

                if (elementType == NodeType.PARAM_PLAYER) {
                    if (resolved instanceof AbstractClientPlayerEntity player) {
                        String name = GameProfileCompatibilityBridge.getName(player.getGameProfile());
                        if (name != null) {
                            snapshot.setParameterValueAndPropagate("Player", name);
                        }
                        if (data != null) {
                            data.targetEntity = player;
                            data.targetBlockPos = player.getBlockPos();
                        }
                        return snapshot;
                    }
                    return null;
                }

                if (elementType == NodeType.PARAM_ITEM) {
                    if (resolved instanceof ItemEntity itemEntity) {
                        ItemStack stack = itemEntity.getStack();
                        if (stack != null && !stack.isEmpty()) {
                            Identifier itemId = Registries.ITEM.getId(stack.getItem());
                            if (itemId != null) {
                                snapshot.setParameterValueAndPropagate("Item", itemId.toString());
                            }
                            if (data != null) {
                                data.targetEntity = itemEntity;
                                data.targetBlockPos = itemEntity.getBlockPos();
                            }
                            return snapshot;
                        }
                    }
                    return null;
                }
            } catch (Exception ignored) {
                return null;
            }
        }

        if (elementType == NodeType.PARAM_GUI) {
            ListSlotEntry slotEntry = owner.parseListSlotEntry(entry);
            if (slotEntry == null) {
                return null;
            }
            Node snapshot = new Node(NodeType.PARAM_INVENTORY_SLOT, 0, 0);
            snapshot.setSocketsHidden(true);
            snapshot.setParameterValueAndPropagate("Slot", Integer.toString(slotEntry.slotIndex));
            snapshot.setParameterValueAndPropagate(
                "Mode",
                InventorySlotModeHelper.buildStoredModeValue("player_inventory", slotEntry.selectionType == SlotSelectionType.PLAYER_INVENTORY)
            );
            if (data != null) {
                data.slotIndex = slotEntry.slotIndex;
                data.slotSelectionType = slotEntry.selectionType;
            }
            return snapshot;
        }
        return null;
    }

    Node resolveListItemValueNode(Node listNode, CompletableFuture<Void> future, boolean reportErrors, RuntimeParameterData data) {
        if (listNode == null) {
            return null;
        }
        ExecutionManager.RuntimeList list = owner.resolveRuntimeList(listNode);
        String listName = Node.getParameterString(listNode, "List");
        String safeListName = listName == null ? "" : listName.trim();
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (list == null || list.isEmpty()) {
            if (reportErrors && client != null) {
                owner.sendNodeErrorMessage(client, tr("pathmind.error.listEmptyOrMissing", safeListName));
            }
            if (reportErrors && future != null && !future.isDone()) {
                future.complete(null);
            }
            return null;
        }

        int index = Node.parseNodeInt(listNode, "Index", 1);
        if (index <= 0 || index > list.size()) {
            if (reportErrors && client != null) {
                owner.sendNodeErrorMessage(client, tr("pathmind.error.listNoItem", safeListName, index));
            }
            if (reportErrors && future != null && !future.isDone()) {
                future.complete(null);
            }
            return null;
        }

        return buildListEntrySnapshot(list, index, reportErrors, data, future);
    }

    private boolean applyNumericOperation(Node snapshot, double amount, String operation, String[] error) {
        if (snapshot == null) {
            return false;
        }
        NodeParameter numericParam = null;
        for (NodeParameter param : snapshot.getParameters()) {
            if (param == null) {
                continue;
            }
            if (param.getType() == ParameterType.INTEGER || param.getType() == ParameterType.DOUBLE) {
                if (numericParam != null) {
                    return false;
                }
                numericParam = param;
            }
        }
        if (numericParam == null) {
            return false;
        }
        String op = owner.normalizeOperation(operation);
        if (numericParam.getType() == ParameterType.INTEGER) {
            if (!isWholeNumber(amount)) {
                if (error != null && error.length > 0) {
                    error[0] = tr("pathmind.error.changeVariableRequiresWholeNumber");
                }
                return false;
            }
            int step = (int) Math.round(amount);
            int current = numericParam.getIntValue();
            switch (op) {
                case "+":
                    numericParam.setIntValue(current + step);
                    break;
                case "-":
                    numericParam.setIntValue(current - step);
                    break;
                case "*":
                    numericParam.setIntValue(current * step);
                    break;
                case "/":
                    if (step == 0) {
                        if (error != null && error.length > 0) {
                            error[0] = tr("pathmind.error.changeVariableDivideByZero");
                        }
                        return false;
                    }
                    numericParam.setIntValue(current / step);
                    break;
                case "%":
                    if (step == 0) {
                        if (error != null && error.length > 0) {
                            error[0] = tr("pathmind.error.changeVariableDivideByZero");
                        }
                        return false;
                    }
                    numericParam.setIntValue(current % step);
                    break;
                default:
                    numericParam.setIntValue(current + step);
                    break;
            }
        } else {
            double current = numericParam.getDoubleValue();
            switch (op) {
                case "+":
                    numericParam.setDoubleValue(current + amount);
                    break;
                case "-":
                    numericParam.setDoubleValue(current - amount);
                    break;
                case "*":
                    numericParam.setDoubleValue(current * amount);
                    break;
                case "/":
                    if (Math.abs(amount) < 1.0E-9) {
                        if (error != null && error.length > 0) {
                            error[0] = tr("pathmind.error.changeVariableDivideByZero");
                        }
                        return false;
                    }
                    numericParam.setDoubleValue(current / amount);
                    break;
                case "%":
                    if (Math.abs(amount) < 1.0E-9) {
                        if (error != null && error.length > 0) {
                            error[0] = tr("pathmind.error.changeVariableDivideByZero");
                        }
                        return false;
                    }
                    numericParam.setDoubleValue(current % amount);
                    break;
                default:
                    numericParam.setDoubleValue(current + amount);
                    break;
            }
        }
        return true;
    }

    private boolean isWholeNumber(double value) {
        return Math.abs(value - Math.rint(value)) < 1.0E-9;
    }
}
