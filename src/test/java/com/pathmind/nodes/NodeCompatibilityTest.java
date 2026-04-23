package com.pathmind.nodes;

import com.pathmind.execution.ExecutionManager;
import com.pathmind.util.RecipeCompatibilityBridge;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeCompatibilityTest {

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

        registerRuntimeList(start);
        manager.setRuntimeList(start, "inventory", new ExecutionManager.RuntimeList(NodeType.PARAM_GUI, List.of("player:9")));

        assertEquals(NodeType.PARAM_INVENTORY_SLOT, listItem.getResolvedValueType());
        assertEquals("9", listItem.exportParameterValues().get("Slot"));
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
        registerRuntimeList(start);
        manager.setRuntimeList(start, "inventory", new ExecutionManager.RuntimeList(NodeType.PARAM_GUI, List.of("player:9")));

        slot.getParameter("Slot").setStringValue("9");
        slot.getParameter("Mode").setStringValue("barrel|player");

        assertTrue(equals.attachParameter(listItem, 0));
        assertTrue(equals.attachParameter(slot, 1));
        assertTrue(equals.evaluateSensor());
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

    private Node coordinateNode(int x, int y, int z) {
        Node coordinate = new Node(NodeType.PARAM_COORDINATE, 0, 0);
        coordinate.getParameter("X").setStringValue(Integer.toString(x));
        coordinate.getParameter("Y").setStringValue(Integer.toString(y));
        coordinate.getParameter("Z").setStringValue(Integer.toString(z));
        return coordinate;
    }

    private void registerRuntimeList(Node start) {
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
