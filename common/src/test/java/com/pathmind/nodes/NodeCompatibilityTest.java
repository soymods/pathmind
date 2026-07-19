package com.pathmind.nodes;

import com.pathmind.data.NodeGraphData;
import com.pathmind.execution.ExecutionManager;
import com.pathmind.routines.RoutineBuilderModel;
import com.pathmind.routines.RoutineValueKind;
import com.pathmind.util.RecipeCompatibilityBridge;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeCompatibilityTest {

    @Test
    void wholeCoordinateComparisonUsesOccupiedBlockPosition() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node position = new Node(NodeType.SENSOR_POSITION_OF, 0, 0);
        Node coordinate = coordinateNode("10", "64", "-20");

        assertEquals(Optional.of(true), new NodeComparisonEvaluator(equals).comparePositionCoordinateValues(
            position, Map.of("X", "10.75", "Y", "64.0", "Z", "-19.25"),
            coordinate, coordinate.exportParameterValues()));
    }

    @Test
    void decimalCoordinateComparisonRemainsPrecise() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node position = new Node(NodeType.SENSOR_POSITION_OF, 0, 0);
        Node coordinate = coordinateNode("10.5", "64", "-19.25");
        NodeComparisonEvaluator evaluator = new NodeComparisonEvaluator(equals);

        assertEquals(Optional.of(true), evaluator.comparePositionCoordinateValues(
            position, Map.of("X", "10.5", "Y", "64.75", "Z", "-19.25"),
            coordinate, coordinate.exportParameterValues()));
        assertEquals(Optional.of(false), evaluator.comparePositionCoordinateValues(
            position, Map.of("X", "10.5001", "Y", "64.75", "Z", "-19.25"),
            coordinate, coordinate.exportParameterValues()));
    }

    @Test
    void greaterOperatorAcceptsPositionOfSensor() {
        Node operator = new Node(NodeType.OPERATOR_GREATER, 0, 0);
        Node sensor = new Node(NodeType.SENSOR_POSITION_OF, 0, 0);

        assertTrue(operator.canAcceptParameterNode(sensor, 0));
        assertTrue(operator.attachParameter(sensor, 0));
    }

    @Test
    void lessOperatorAcceptsDistanceBetweenSensor() {
        Node operator = new Node(NodeType.OPERATOR_LESS, 0, 0);
        Node sensor = new Node(NodeType.SENSOR_DISTANCE_BETWEEN, 0, 0);

        assertTrue(operator.canAcceptParameterNode(sensor, 0));
        assertTrue(operator.attachParameter(sensor, 0));
    }

    @Test
    void slotItemCountSensorAcceptsInventorySlotParameter() {
        Node sensor = new Node(NodeType.SENSOR_SLOT_ITEM_COUNT, 0, 0);
        Node slot = new Node(NodeType.PARAM_INVENTORY_SLOT, 0, 0);

        assertTrue(sensor.hasParameterSlot());
        assertTrue(sensor.canAcceptParameterNode(slot, 0));
        assertTrue(sensor.attachParameter(slot, 0));
    }

    @Test
    void distanceBetweenAcceptsUserParameter() {
        Node sensor = new Node(NodeType.SENSOR_DISTANCE_BETWEEN, 0, 0);
        Node user = new Node(NodeType.PARAM_PLAYER, 0, 0);

        assertTrue(sensor.canAcceptParameterNode(user, 0));
        assertTrue(sensor.attachParameter(user, 0));
    }

    @Test
    void distanceBetweenAcceptsPositionOfEntity() {
        Node sensor = new Node(NodeType.SENSOR_DISTANCE_BETWEEN, 0, 0);
        Node positionOf = new Node(NodeType.SENSOR_POSITION_OF, 0, 0);
        Node entity = new Node(NodeType.PARAM_ENTITY, 0, 0);

        assertTrue(positionOf.attachParameter(entity, 0));
        assertTrue(sensor.canAcceptParameterNode(positionOf, 0));
        assertTrue(sensor.attachParameter(positionOf, 0));
    }

    @Test
    void positionOfAcceptsTargetedEntitySensor() {
        Node positionOf = new Node(NodeType.SENSOR_POSITION_OF, 0, 0);
        Node targetedEntity = new Node(NodeType.SENSOR_TARGETED_ENTITY, 0, 0);

        assertTrue(positionOf.canAcceptParameterNode(targetedEntity, 0));
        assertTrue(positionOf.attachParameter(targetedEntity, 0));
    }

    @Test
    void distanceBetweenAcceptsTargetedBlockSensor() {
        Node sensor = new Node(NodeType.SENSOR_DISTANCE_BETWEEN, 0, 0);
        Node targetedBlock = new Node(NodeType.SENSOR_TARGETED_BLOCK, 0, 0);

        assertTrue(sensor.canAcceptParameterNode(targetedBlock, 0));
        assertTrue(sensor.attachParameter(targetedBlock, 0));
    }

    @Test
    void slotItemCountAcceptsCurrentHandSensor() {
        Node sensor = new Node(NodeType.SENSOR_SLOT_ITEM_COUNT, 0, 0);
        Node currentHand = new Node(NodeType.SENSOR_CURRENT_HAND, 0, 0);

        assertTrue(sensor.canAcceptParameterNode(currentHand, 0));
        assertTrue(sensor.attachParameter(currentHand, 0));
    }

    @Test
    void equalsAcceptsCurrentGuiSensorAsParameterValue() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node currentGui = new Node(NodeType.SENSOR_CURRENT_GUI, 0, 0);
        Node gui = new Node(NodeType.PARAM_GUI, 0, 0);

        assertTrue(currentGui.isParameterNode());
        assertTrue(currentGui.usesMinimalNodePresentation());
        assertTrue(equals.canAcceptParameterNode(currentGui, 0));
        assertTrue(equals.canAcceptParameterNode(gui, 1));
        assertTrue(equals.attachParameter(currentGui, 0));
        assertTrue(equals.attachParameter(gui, 1));
    }

    @Test
    void fabricEventSensorDoesNotExposeParameterSlot() {
        Node sensor = new Node(NodeType.SENSOR_FABRIC_EVENT, 0, 0);
        Node amount = new Node(NodeType.PARAM_AMOUNT, 0, 0);

        assertFalse(sensor.hasParameterSlot());
        assertFalse(sensor.canAcceptParameterNode(amount, 0));
    }

    @Test
    void createListTreatsBlockTargetsAsCollectionSources() {
        assertTrue(Node.isCreateListCollectionTarget(NodeType.PARAM_BLOCK));
        assertTrue(Node.isCreateListCollectionTarget(NodeType.PARAM_ENTITY));
        assertTrue(Node.isCreateListCollectionTarget(NodeType.PARAM_PLAYER));
        assertTrue(Node.isCreateListCollectionTarget(NodeType.PARAM_ITEM));
        assertTrue(Node.isCreateListCollectionTarget(NodeType.PARAM_GUI));
        assertFalse(Node.isCreateListCollectionTarget(NodeType.PARAM_COORDINATE));
    }

    @Test
    void createListClassifiesVariableResolvedBlockTargetAsCollectionSource() {
        String variableName = "block_target_" + System.nanoTime();
        Node createList = new Node(NodeType.CREATE_LIST, 0, 0);
        Node variable = new Node(NodeType.VARIABLE, 0, 0);
        variable.setParameterValueAndPropagate("Variable", variableName);
        ExecutionManager.getInstance().setRuntimeVariableForAnyActiveChain(variableName,
            new ExecutionManager.RuntimeVariable(NodeType.PARAM_BLOCK, Map.of(
                "Block", "minecraft:azalea_leaves",
                "block", "minecraft:azalea_leaves"
            )));

        Node resolved = new NodeVariableListCommandExecutor(createList)
            .resolveCreateListTargetParameter(variable, null);

        assertEquals(NodeType.PARAM_BLOCK, resolved.getType());
        assertTrue(Node.isCreateListCollectionTarget(resolved.getType()));
    }

    @Test
    void routineInputCanAttachAnywhereVariableCan() {
        Node dropSlot = new Node(NodeType.DROP_SLOT, 0, 0);
        Node input = new Node(NodeType.ROUTINE_INPUT, 0, 0);
        input.getParameter("ValueKind").setStringValue("BLOCK");

        assertTrue(dropSlot.canAcceptParameterNode(input, 0));
        assertTrue(dropSlot.attachParameter(input, 0));
    }

    @Test
    void routineCallInputBoxAcceptsAnyParameterType() {
        NodeGraphData.RoutineDefinitionData routine = RoutineBuilderModel.createRoutine("Use");
        RoutineBuilderModel builder = new RoutineBuilderModel(routine);
        NodeGraphData.RoutineInputData input = builder.addInput("message", RoutineValueKind.TEXT);
        Node call = Node.createRoutineCall(routine, 0, 0);
        Node block = new Node(NodeType.PARAM_BLOCK, 0, 0);

        int slot = call.getRoutineSlotForInputId(input.getId());
        assertTrue(call.canAcceptParameterNode(block, slot));
        assertTrue(call.attachParameter(block, slot));
    }

    @Test
    void recipeCacheUsableRequiresAtLeastOneValidRecipeEntry() {
        assertFalse(Node.isRecipeCacheUsableForTests(Map.of()));
        assertFalse(Node.isRecipeCacheUsableForTests(Map.of(
            "minecraft:stick", List.of(Map.of(
                "mode", "CRAFT_CRAFTING_TABLE",
                "outputCount", 1,
                "grid", List.of(Map.of("slotIndex", 0, "itemIds", List.of()))
            ))
        )));
        assertTrue(Node.isRecipeCacheUsableForTests(Map.of(
            "minecraft:stick", List.of(Map.of(
                "mode", "CRAFT_CRAFTING_TABLE",
                "outputCount", 4,
                "grid", List.of(Map.of("slotIndex", 0, "itemIds", List.of("minecraft:oak_planks")))
            ))
        )));
    }

    @Test
    void ingredientSourcePlannerAllowsRepeatedIngredientsFromSameStack() {
        List<Integer> plannedSlots = Node.planIngredientSourceSlotsForTests(
            List.of(new Node.TestIngredientStack("minecraft:oak_planks", 2)),
            List.of("minecraft:oak_planks", "minecraft:oak_planks")
        );

        assertEquals(List.of(0, 0), plannedSlots);
    }

    @Test
    void ingredientSourcePlannerFailsWhenRepeatedIngredientsExceedStackCount() {
        List<Integer> plannedSlots = Node.planIngredientSourceSlotsForTests(
            List.of(new Node.TestIngredientStack("minecraft:oak_planks", 1)),
            List.of("minecraft:oak_planks", "minecraft:oak_planks")
        );

        assertNull(plannedSlots);
    }

    @Test
    void cachedRecipeSlotNormalizationUpgradesLegacyZeroBasedSlots() {
        assertEquals(List.of(1, 2, 4), Node.normalizeCachedRecipeSlotIndexesForTests(List.of(0, 1, 3)));
        assertEquals(List.of(1, 2, 3, 4), Node.normalizeCachedRecipeSlotIndexesForTests(List.of(1, 2, 3, 4)));
    }

    @Test
    void resetRecipeCacheWarmupClearsLoadedRecipeCacheBook() throws Exception {
        Field cachedRecipeBookField = Node.class.getDeclaredField("cachedRecipeBook");
        cachedRecipeBookField.setAccessible(true);

        Class<?> cachedRecipeBookClass = Class.forName("com.pathmind.nodes.Node$CachedRecipeBook");
        Constructor<?> constructor = cachedRecipeBookClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object cachedBook = constructor.newInstance();
        cachedRecipeBookField.set(null, cachedBook);

        Node.resetRecipeCacheWarmup();

        assertNull(cachedRecipeBookField.get(null));
    }

    @Test
    void recipeCacheWarmupRequiresAnActiveSingleplayerServer() {
        Node.resetRecipeCacheWarmup();

        assertFalse(Node.requestRecipeCacheWarmup(null));
        assertFalse(Node.isRecipeCacheWarmupRequested());
    }

    @Test
    void resetRecipeCacheWarmupClearsManualWarmupRequest() throws Exception {
        Field requestedField = NodeCraftCommandExecutor.class.getDeclaredField("recipeCacheWarmupRequested");
        requestedField.setAccessible(true);
        requestedField.setBoolean(null, true);

        Node.resetRecipeCacheWarmup();

        assertFalse(Node.isRecipeCacheWarmupRequested());
    }

    @Test
    void recipeDisplayBridgeSupportsObfuscatedEntryAndDisplayAccessors() {
        DummyShapedDisplay display = new DummyShapedDisplay();
        DummyDisplayEntry entry = new DummyDisplayEntry(display);
        DummyRecipeCollection collection = new DummyRecipeCollection(List.of(entry));

        assertSame(display, RecipeCompatibilityBridge.getDisplayFromEntry(entry));
        assertTrue(RecipeCompatibilityBridge.isCraftingDisplay(display));
        assertTrue(RecipeCompatibilityBridge.isShapedCraftingDisplay(display));
        assertFalse(RecipeCompatibilityBridge.isShapelessCraftingDisplay(display));
        assertEquals(2, RecipeCompatibilityBridge.getShapedWidth(display));
        assertEquals(1, RecipeCompatibilityBridge.getShapedHeight(display));
        assertEquals(display.comp_3270(), RecipeCompatibilityBridge.getDisplayIngredientSlots(display));
        assertEquals(List.of(entry), RecipeCompatibilityBridge.getAllRecipesFromCollection(collection));
    }

    @Test
    void recipeDisplayBridgeRecognizesObfuscatedShapelessDisplays() {
        DummyShapelessDisplay display = new DummyShapelessDisplay();

        assertTrue(RecipeCompatibilityBridge.isCraftingDisplay(display));
        assertFalse(RecipeCompatibilityBridge.isShapedCraftingDisplay(display));
        assertTrue(RecipeCompatibilityBridge.isShapelessCraftingDisplay(display));
        assertEquals(display.comp_3271(), RecipeCompatibilityBridge.getDisplayIngredientSlots(display));
    }

    @Test
    void lookAcceptsAmountParameter() {
        Node look = new Node(NodeType.LOOK, 0, 0);
        Node amount = new Node(NodeType.PARAM_AMOUNT, 0, 0);
        amount.getParameter("Amount").setStringValue("-45");

        assertTrue(look.canAcceptParameterNode(amount, 0));
        assertTrue(look.attachParameter(amount, 0));
    }

    @Test
    void orOperatorAcceptsCoordinateParameters() {
        Node or = new Node(NodeType.OPERATOR_BOOLEAN_OR, 0, 0);
        Node coordinate = new Node(NodeType.PARAM_COORDINATE, 0, 0);

        assertTrue(or.canAcceptParameterNode(coordinate, 0));
        assertTrue(or.attachParameter(coordinate, 0));
    }

    @Test
    void uiUtilsDelayPacketModesDoNotExposeEnabledParameter() {
        Node delayPackets = new Node(NodeType.UI_UTILS, 0, 0);
        delayPackets.setMode(NodeMode.UI_UTILS_ENABLE_DELAY_PACKETS);

        Node disableDelay = new Node(NodeType.UI_UTILS, 0, 0);
        disableDelay.setMode(NodeMode.UI_UTILS_DISABLE_DELAY_PACKETS);

        assertNull(delayPackets.getParameter("Enabled"));
        assertNull(disableDelay.getParameter("Enabled"));
    }

    @Test
    void uiUtilsModePickerUsesSingleDelayToggleMode() {
        List<NodeMode> modes = List.of(NodeMode.getModesForNodeType(NodeType.UI_UTILS));

        assertTrue(modes.contains(NodeMode.UI_UTILS_ENABLE_DELAY_PACKETS));
        assertFalse(modes.contains(NodeMode.UI_UTILS_DISABLE_DELAY_PACKETS));
        assertFalse(modes.contains(NodeMode.UI_UTILS_SET_SEND_PACKETS));
        assertFalse(modes.contains(NodeMode.UI_UTILS_SET_DELAY_PACKETS));
    }

    @Test
    void villagerTradeUsesDisplayedDiscountedPriceForFirstBuyItem() {
        assertEquals(3, Node.resolveRequiredTradeCountForTests(3, 5));
        assertEquals(5, Node.resolveRequiredTradeCountForTests(0, 5));
        assertEquals(0, Node.resolveRequiredTradeCountForTests(0, 0));
    }

    @Test
    void controlRepeatFallsThroughSingleOutputAfterFinalIteration() throws Exception {
        Node repeat = new Node(NodeType.CONTROL_REPEAT, 0, 0);
        repeat.getParameter("Count").setStringValue("2");
        Method executeControlRepeat = Node.class.getDeclaredMethod("executeControlRepeat", java.util.concurrent.CompletableFuture.class);
        executeControlRepeat.setAccessible(true);

        java.util.concurrent.CompletableFuture<Void> first = new java.util.concurrent.CompletableFuture<>();
        executeControlRepeat.invoke(repeat, first);
        assertTrue(first.isDone());
        assertEquals(0, repeat.consumeNextOutputSocket());
        assertTrue(repeat.shouldExecuteRepeatAttachedAction());

        java.util.concurrent.CompletableFuture<Void> second = new java.util.concurrent.CompletableFuture<>();
        executeControlRepeat.invoke(repeat, second);
        assertTrue(second.isDone());
        assertEquals(0, repeat.consumeNextOutputSocket());
        assertTrue(repeat.shouldExecuteRepeatAttachedAction());

        java.util.concurrent.CompletableFuture<Void> exit = new java.util.concurrent.CompletableFuture<>();
        executeControlRepeat.invoke(repeat, exit);
        assertTrue(exit.isDone());
        assertEquals(0, repeat.consumeNextOutputSocket());
        assertFalse(repeat.shouldExecuteRepeatAttachedAction());
    }

    @Test
    void equalsSupportsCoordinateOrGroupComparisons() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node position = coordinateNode(10, 64, 10);
        Node or = new Node(NodeType.OPERATOR_BOOLEAN_OR, 0, 0);
        Node first = coordinateNode(0, 64, 0);
        Node second = coordinateNode(10, 64, 10);

        assertTrue(or.attachParameter(first, 0));
        assertTrue(or.attachParameter(second, 1));
        assertTrue(equals.attachParameter(position, 0));
        assertTrue(equals.attachParameter(or, 1));
        assertTrue(equals.evaluateSensor());
    }

    @Test
    void notSupportsCoordinateOrGroupComparisons() {
        Node not = new Node(NodeType.OPERATOR_NOT, 0, 0);
        Node position = coordinateNode(10, 64, 10);
        Node or = new Node(NodeType.OPERATOR_BOOLEAN_OR, 0, 0);
        Node first = coordinateNode(0, 64, 0);
        Node second = coordinateNode(10, 64, 10);

        assertTrue(or.attachParameter(first, 0));
        assertTrue(or.attachParameter(second, 1));
        assertTrue(not.attachParameter(position, 0));
        assertTrue(not.attachParameter(or, 1));
        org.junit.jupiter.api.Assertions.assertFalse(not.evaluateSensor());
    }

    @Test
    void booleanOrStillEvaluatesBooleanInputs() {
        Node or = new Node(NodeType.OPERATOR_BOOLEAN_OR, 0, 0);
        Node left = new Node(NodeType.PARAM_BOOLEAN, 0, 0);
        Node right = new Node(NodeType.PARAM_BOOLEAN, 0, 0);
        left.getParameter("Toggle").setStringValue("false");
        right.getParameter("Toggle").setStringValue("true");

        assertTrue(or.attachParameter(left, 0));
        assertTrue(or.attachParameter(right, 1));
        assertTrue(or.evaluateSensor());
    }

    @Test
    void andOperatorAcceptsCoordinateParameters() {
        Node and = new Node(NodeType.OPERATOR_BOOLEAN_AND, 0, 0);
        Node coordinate = new Node(NodeType.PARAM_COORDINATE, 0, 0);

        assertTrue(and.canAcceptParameterNode(coordinate, 0));
        assertTrue(and.attachParameter(coordinate, 0));
    }

    @Test
    void equalsSupportsCoordinateAndGroupComparisons() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node position = coordinateNode(10, 64, 10);
        Node and = new Node(NodeType.OPERATOR_BOOLEAN_AND, 0, 0);
        Node first = coordinateNode(10, 64, 10);
        Node second = coordinateNode(10, 64, 10);

        assertTrue(and.attachParameter(first, 0));
        assertTrue(and.attachParameter(second, 1));
        assertTrue(equals.attachParameter(position, 0));
        assertTrue(equals.attachParameter(and, 1));
        assertTrue(equals.evaluateSensor());
    }

    @Test
    void equalsSupportsItemComparisonsAcrossDifferentItemNodeShapes() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node item = new Node(NodeType.PARAM_ITEM, 0, 0);
        Node trade = new Node(NodeType.PARAM_VILLAGER_TRADE, 0, 0);

        item.getParameter("Item").setStringValue("minecraft:emerald");
        trade.getParameter("Item").setStringValue("emerald");

        assertTrue(equals.attachParameter(item, 0));
        assertTrue(equals.attachParameter(trade, 1));
        assertTrue(equals.evaluateSensor());
    }

    @Test
    void equalsFailsWhenItemSelectionsDiffer() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node left = new Node(NodeType.PARAM_ITEM, 0, 0);
        Node right = new Node(NodeType.PARAM_VILLAGER_TRADE, 0, 0);

        left.getParameter("Item").setStringValue("minecraft:emerald");
        right.getParameter("Item").setStringValue("diamond");

        assertTrue(equals.attachParameter(left, 0));
        assertTrue(equals.attachParameter(right, 1));
        assertFalse(equals.evaluateSensor());
    }

    private static final class DummyDisplayEntry {
        private final Object display;

        private DummyDisplayEntry(Object display) {
            this.display = display;
        }

        public Object comp_3263() {
            return display;
        }
    }

    private static final class DummyRecipeCollection {
        private final List<Object> field_54835;

        private DummyRecipeCollection(List<Object> entries) {
            this.field_54835 = new ArrayList<>(entries);
        }
    }

    private static final class DummyShapedDisplay {
        private final List<Object> slots = List.of("stone", "stone", "stone");

        public int comp_3268() {
            return 2;
        }

        public int comp_3269() {
            return 1;
        }

        public List<Object> comp_3270() {
            return slots;
        }

        public Object comp_3258() {
            return "result";
        }
    }

    private static final class DummyShapelessDisplay {
        private final List<Object> ingredients = List.of("a", "b");

        public List<Object> comp_3271() {
            return ingredients;
        }

        public Object comp_3258() {
            return "result";
        }
    }

    @Test
    void guiListItemsResolveAsInventorySlots() {
        ExecutionManager manager = ExecutionManager.getInstance();
        Node start = new Node(NodeType.START, 0, 0);
        Node listItem = new Node(NodeType.LIST_ITEM, 0, 0);
        listItem.setOwningStartNode(start);
        listItem.getParameter("List").setStringValue("inventory");
        listItem.getParameter("Index").setStringValue("1");

        registerRuntimeChain(start);
        manager.setRuntimeList(start, "inventory", new ExecutionManager.RuntimeList(NodeType.PARAM_GUI, List.of("player:9")));

        assertEquals(NodeType.PARAM_INVENTORY_SLOT, listItem.getResolvedValueType());
        assertEquals("9", listItem.exportParameterValues().get("Slot"));
    }

    @Test
    void nestedListNodesKeepIndependentListNames() {
        Node addToList = new Node(NodeType.ADD_TO_LIST, 0, 0);
        Node listItem = new Node(NodeType.LIST_ITEM, 0, 0);

        assertTrue(addToList.attachParameter(listItem, 0));

        addToList.setParameterValueAndPropagate("List", "outer");
        listItem.setParameterValueAndPropagate("List", "inner");

        assertEquals("outer", addToList.getParameter("List").getStringValue());
        assertEquals("inner", listItem.getParameter("List").getStringValue());
    }

    @Test
    void equalsTreatsInventorySlotsWithSameContextAsEqualAcrossModes() {
        ExecutionManager manager = ExecutionManager.getInstance();
        Node start = new Node(NodeType.START, 0, 0);
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node listItem = new Node(NodeType.LIST_ITEM, 0, 0);
        Node slot = new Node(NodeType.PARAM_INVENTORY_SLOT, 0, 0);

        listItem.setOwningStartNode(start);
        listItem.getParameter("List").setStringValue("inventory");
        listItem.getParameter("Index").setStringValue("1");
        registerRuntimeChain(start);
        manager.setRuntimeList(start, "inventory", new ExecutionManager.RuntimeList(NodeType.PARAM_GUI, List.of("player:9")));

        slot.getParameter("Slot").setStringValue("9");
        slot.getParameter("Mode").setStringValue("barrel|player");

        assertTrue(equals.attachParameter(listItem, 0));
        assertTrue(equals.attachParameter(slot, 1));
        assertTrue(equals.evaluateSensor());
    }

    @Test
    void equalsComparesInventorySlotAndItemBeforeNumericSlotCount() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node slot = new Node(NodeType.PARAM_INVENTORY_SLOT, 0, 0) {
            @Override
            public Map<String, String> exportParameterValues() {
                return Map.ofEntries(
                    Map.entry("Slot", "0"),
                    Map.entry("slot", "0"),
                    Map.entry("Mode", "shulker_box|container"),
                    Map.entry("mode", "shulker_box|container"),
                    Map.entry("Item", "minecraft:emerald"),
                    Map.entry("item", "minecraft:emerald"),
                    Map.entry("Items", "minecraft:emerald"),
                    Map.entry("items", "minecraft:emerald"),
                    Map.entry("Count", "64"),
                    Map.entry("count", "64"),
                    Map.entry("Amount", "64"),
                    Map.entry("amount", "64")
                );
            }
        };
        Node item = new Node(NodeType.PARAM_ITEM, 0, 0);
        item.getParameter("Item").setStringValue("emerald");

        assertTrue(equals.attachParameter(slot, 0));
        assertTrue(equals.attachParameter(item, 1));
        assertTrue(equals.evaluateSensor());
    }

    @Test
    void moveItemResolvesInventorySlotVariableSelectionMode() {
        ExecutionManager manager = ExecutionManager.getInstance();
        Node start = new Node(NodeType.START, 0, 0);
        Node moveItem = new Node(NodeType.MOVE_ITEM, 0, 0);
        Node variable = new Node(NodeType.VARIABLE, 0, 0);

        moveItem.setOwningStartNode(start);
        variable.getParameter("Variable").setStringValue("slot");
        registerRuntimeChain(start);
        manager.setRuntimeVariable(start, "slot", new ExecutionManager.RuntimeVariable(
            NodeType.PARAM_INVENTORY_SLOT,
            Map.of(
                "Slot", "0",
                "slot", "0",
                "SourceSlot", "0",
                "sourceslot", "0",
                "Mode", "double_chest|container",
                "mode", "double_chest|container"
            )
        ));

        assertTrue(moveItem.attachParameter(variable, 0));

        assertEquals(SlotSelectionType.GUI_CONTAINER, moveItem.resolveInventorySlotSelectionType(variable));
    }

    @Test
    void equalsFailsWhenCoordinateAndGroupContainsMismatch() {
        Node equals = new Node(NodeType.OPERATOR_EQUALS, 0, 0);
        Node position = coordinateNode(10, 64, 10);
        Node and = new Node(NodeType.OPERATOR_BOOLEAN_AND, 0, 0);
        Node first = coordinateNode(10, 64, 10);
        Node second = coordinateNode(0, 64, 0);

        assertTrue(and.attachParameter(first, 0));
        assertTrue(and.attachParameter(second, 1));
        assertTrue(equals.attachParameter(position, 0));
        assertTrue(equals.attachParameter(and, 1));
        org.junit.jupiter.api.Assertions.assertFalse(equals.evaluateSensor());
    }

    @Test
    void booleanAndStillEvaluatesBooleanInputs() {
        Node and = new Node(NodeType.OPERATOR_BOOLEAN_AND, 0, 0);
        Node left = new Node(NodeType.PARAM_BOOLEAN, 0, 0);
        Node right = new Node(NodeType.PARAM_BOOLEAN, 0, 0);
        left.getParameter("Toggle").setStringValue("true");
        right.getParameter("Toggle").setStringValue("true");

        assertTrue(and.attachParameter(left, 0));
        assertTrue(and.attachParameter(right, 1));
        assertTrue(and.evaluateSensor());
    }

    @Test
    void standaloneCalculateStoresResolvedRuntimeVariableExpression() {
        Node start = new Node(NodeType.START, 0, 0);
        Node setVariable1 = new Node(NodeType.SET_VARIABLE, 0, 0);
        Node variable1 = new Node(NodeType.VARIABLE, 0, 0);
        Node amount1 = new Node(NodeType.PARAM_AMOUNT, 0, 0);
        Node setVariable2 = new Node(NodeType.SET_VARIABLE, 0, 0);
        Node variable2 = new Node(NodeType.VARIABLE, 0, 0);
        Node amount2 = new Node(NodeType.PARAM_AMOUNT, 0, 0);
        Node calculate = new Node(NodeType.CHANGE_VARIABLE, 0, 0);
        Node message = new Node(NodeType.MESSAGE, 0, 0);

        setVariable1.setOwningStartNode(start);
        setVariable2.setOwningStartNode(start);
        calculate.setOwningStartNode(start);
        message.setOwningStartNode(start);

        variable1.getParameter("Variable").setStringValue("variable1");
        amount1.getParameter("Amount").setStringValue("1");
        assertTrue(setVariable1.attachParameter(variable1, 0));
        assertTrue(setVariable1.attachParameter(amount1, 1));

        variable2.getParameter("Variable").setStringValue("variable2");
        amount2.getParameter("Amount").setStringValue("4");
        assertTrue(setVariable2.attachParameter(variable2, 0));
        assertTrue(setVariable2.attachParameter(amount2, 1));

        calculate.setMessageLine(0, "A = $variable1*$variable2");

        java.util.concurrent.CompletableFuture<Void> setFirst = new java.util.concurrent.CompletableFuture<>();
        new NodeVariableListCommandExecutor(setVariable1).executeSetVariableCommand(setFirst);
        assertTrue(setFirst.isDone());
        assertFalse(setFirst.isCompletedExceptionally());

        java.util.concurrent.CompletableFuture<Void> setSecond = new java.util.concurrent.CompletableFuture<>();
        new NodeVariableListCommandExecutor(setVariable2).executeSetVariableCommand(setSecond);
        assertTrue(setSecond.isDone());
        assertFalse(setSecond.isCompletedExceptionally());

        java.util.concurrent.CompletableFuture<Void> calculated = new java.util.concurrent.CompletableFuture<>();
        new NodeVariableListCommandExecutor(calculate)
            .executeChangeVariableCommand(calculated);
        assertTrue(calculated.isDone());
        assertFalse(calculated.isCompletedExceptionally());

        assertEquals("4", message.resolveRuntimeVariablesInText("$A"));
        assertEquals("Value: 4.", message.resolveRuntimeVariablesInText("Value: $A."));
        assertEquals("(4), [4]; \"4\"!", message.resolveRuntimeVariablesInText("($A), [$A]; \"$A\"!"));
        assertEquals("$ASuffix", message.resolveRuntimeVariablesInText("$ASuffix"));
        assertEquals("8", message.resolveRuntimeVariablesInText("$A*2"));
    }

    @Test
    void setVariableStoresModuloAsSingleNumericRuntimeValue() {
        Node start = new Node(NodeType.START, 0, 0);
        Node setVariable = new Node(NodeType.SET_VARIABLE, 0, 0);
        Node variable = new Node(NodeType.VARIABLE, 0, 0);
        Node modulo = new Node(NodeType.OPERATOR_MOD, 0, 0);
        Node value = new Node(NodeType.PARAM_AMOUNT, 0, 0);
        Node divisor = new Node(NodeType.PARAM_AMOUNT, 0, 0);
        Node message = new Node(NodeType.MESSAGE, 0, 0);

        setVariable.setOwningStartNode(start);
        message.setOwningStartNode(start);
        variable.getParameter("Variable").setStringValue("modResult");
        value.getParameter("Amount").setStringValue("10");
        divisor.getParameter("Amount").setStringValue("3");

        assertTrue(modulo.attachParameter(value, 0));
        assertTrue(modulo.attachParameter(divisor, 1));
        assertTrue(setVariable.attachParameter(variable, 0));
        assertTrue(setVariable.attachParameter(modulo, 1));

        java.util.concurrent.CompletableFuture<Void> stored = new java.util.concurrent.CompletableFuture<>();
        new NodeVariableListCommandExecutor(setVariable).executeSetVariableCommand(stored);

        assertTrue(stored.isDone());
        assertFalse(stored.isCompletedExceptionally());
        assertEquals("1", message.resolveRuntimeVariablesInText("$modResult"));
    }

    @Test
    void standaloneCalculateExecutesWithoutVariableAttachment() {
        Node start = new Node(NodeType.START, 0, 0);
        Node calculate = new Node(NodeType.CHANGE_VARIABLE, 0, 0);
        Node message = new Node(NodeType.MESSAGE, 0, 0);
        assertFalse(calculate.hasParameterSlot());
        calculate.setOwningStartNode(start);
        message.setOwningStartNode(start);
        calculate.setMessageLine(0, "standaloneExecutionResult = 2 + 3");
        registerRuntimeChain(start);

        java.util.concurrent.CompletableFuture<Void> execution = calculate.execute();

        assertTrue(execution.isDone());
        assertFalse(execution.isCompletedExceptionally());
        assertEquals("5", message.resolveRuntimeVariablesInText("$standaloneExecutionResult"));
    }

    @Test
    void executionSnapshotPreservesCalculateMessageLines() throws Exception {
        ExecutionManager manager = ExecutionManager.getInstance();
        Node calculate = new Node(NodeType.CHANGE_VARIABLE, 0, 0);
        calculate.setMessageLine(0, "A = $variable1*$variable2");

        Method createGraphSnapshot = ExecutionManager.class.getDeclaredMethod("createGraphSnapshot", List.class, List.class);
        createGraphSnapshot.setAccessible(true);
        NodeGraphData snapshot = (NodeGraphData) createGraphSnapshot.invoke(manager, List.of(calculate), List.of());
        NodeGraphData.NodeData clonedCalculate = snapshot.getNodes().stream()
            .filter(node -> calculate.getId().equals(node.getId()))
            .findFirst()
            .orElseThrow();

        assertEquals(List.of("A = $variable1*$variable2"), clonedCalculate.getMessageLines());
    }

    private Node coordinateNode(int x, int y, int z) {
        return coordinateNode(Integer.toString(x), Integer.toString(y), Integer.toString(z));
    }

    private Node coordinateNode(String x, String y, String z) {
        Node coordinate = new Node(NodeType.PARAM_COORDINATE, 0, 0);
        coordinate.getParameter("X").setStringValue(x);
        coordinate.getParameter("Y").setStringValue(y);
        coordinate.getParameter("Z").setStringValue(z);
        return coordinate;
    }

    private void registerRuntimeChain(Node start) {
        try {
            ExecutionManager manager = ExecutionManager.getInstance();
            Class<?> controllerClass = List.of(ExecutionManager.class.getDeclaredClasses()).stream()
                .filter(candidate -> "ChainController".equals(candidate.getSimpleName()))
                .findFirst()
                .orElseThrow();
            Constructor<?> constructor = controllerClass.getDeclaredConstructor(Node.class, int.class);
            constructor.setAccessible(true);
            Object controller = constructor.newInstance(start, 1);
            Field activeChainsField = ExecutionManager.class.getDeclaredField("activeChains");
            activeChainsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Node, Object> activeChains = (Map<Node, Object>) activeChainsField.get(manager);
            activeChains.put(start, controller);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
