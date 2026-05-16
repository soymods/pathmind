package com.pathmind.nodes;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeBehaviorDefinitionRegistryTest {

    @Test
    void targetParameterFamilyHasConsolidatedDefinitions() {
        NodeBehaviorDefinition item = NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_ITEM);
        NodeBehaviorDefinition block = NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_BLOCK);
        NodeBehaviorDefinition entity = NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_ENTITY);
        NodeBehaviorDefinition player = NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_PLAYER);

        assertNotNull(item);
        assertNotNull(block);
        assertNotNull(entity);
        assertNotNull(player);

        assertTrue(item.hasParameterBehavior());
        assertTrue(item.hasRuntimeBehavior());
        assertTrue(item.hasListEntryBehavior());
        assertTrue(item.hasGotoFallbackTargetBehavior());

        assertTrue(block.hasParameterBehavior());
        assertTrue(block.hasRuntimeBehavior());
        assertTrue(block.hasGotoFallbackTargetBehavior());

        assertTrue(entity.hasRuntimeBehavior());
        assertTrue(entity.hasListEntryBehavior());
        assertTrue(entity.hasGotoFallbackTargetBehavior());

        assertTrue(player.hasRuntimeBehavior());
        assertTrue(player.hasListEntryBehavior());
        assertTrue(player.hasGotoFallbackTargetBehavior());
    }

    @Test
    void comparableAndRuntimeFamiliesAreAvailableThroughUnifiedDefinitions() {
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_COORDINATE).hasRuntimeBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_COORDINATE).hasComparableBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_DIRECTION).hasParameterBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_DIRECTION).hasRuntimeBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_DIRECTION).hasComparableBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_BLOCK_FACE).hasParameterBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_BLOCK_FACE).hasRuntimeBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_BLOCK_FACE).hasComparableBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_CLOSEST).hasParameterBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_CLOSEST).hasRuntimeBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_AMOUNT).hasParameterBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_AMOUNT).hasComparableBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_DURATION).hasParameterBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_BOOLEAN).hasParameterBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_HAND).hasParameterBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_MESSAGE).hasParameterBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_MESSAGE).hasComparableBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_INVENTORY_SLOT).hasParameterBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_INVENTORY_SLOT).hasComparableBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_GUI).hasParameterBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_KEY).hasParameterBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_KEY).hasComparableBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_MOUSE_BUTTON).hasParameterBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_MOUSE_BUTTON).hasComparableBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.get(NodeType.LIST_ITEM).hasGotoFallbackTargetBehavior());
        assertTrue(NodeBehaviorDefinitionRegistry.snapshot().containsKey(NodeType.PARAM_DIRECTION));
    }

    @Test
    void itemDefinitionOwnsExportAliasesDirectly() {
        Node item = new Node(NodeType.PARAM_ITEM, 0, 0);
        Map<String, String> values = new HashMap<>();
        values.put("Item", "minecraft:diamond");
        values.put("Amount", "3");

        Map<String, String> exported = NodeBehaviorDefinitionRegistry
            .get(NodeType.PARAM_ITEM)
            .exportValues(item, values);

        assertEquals("minecraft:diamond", exported.get("Items"));
        assertEquals("3", exported.get("Count"));
        assertEquals("3", exported.get("count"));
    }

    @Test
    void coordinateDefinitionResolvesPositionAndComparableString() {
        Node owner = new Node(NodeType.LOOK, 0, 0);
        Node coordinate = new Node(NodeType.PARAM_COORDINATE, 0, 0);
        coordinate.getParameter("X").setStringValue("10");
        coordinate.getParameter("Y").setStringValue("64");
        coordinate.getParameter("Z").setStringValue("-3");
        RuntimeParameterData data = new RuntimeParameterData();
        NodeBehaviorDefinition definition = NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_COORDINATE);

        Optional<Vec3d> resolved = definition.resolvePositionTarget(owner, coordinate, data, null);

        assertEquals(Optional.of(Vec3d.ofCenter(new BlockPos(10, 64, -3))), resolved);
        assertEquals(new BlockPos(10, 64, -3), data.targetBlockPos);
        assertEquals(Optional.of("10 64 -3"), definition.resolveComparableString(owner, coordinate));
    }

    @Test
    void schematicDefinitionStoresSchematicName() {
        Node owner = new Node(NodeType.BUILD, 0, 0);
        Node schematic = new Node(NodeType.PARAM_SCHEMATIC, 0, 0);
        schematic.getParameter("Schematic").setStringValue("house");
        schematic.getParameter("X").setStringValue("1");
        schematic.getParameter("Y").setStringValue("2");
        schematic.getParameter("Z").setStringValue("3");
        RuntimeParameterData data = new RuntimeParameterData();

        Optional<Vec3d> resolved = NodeBehaviorDefinitionRegistry
            .get(NodeType.PARAM_SCHEMATIC)
            .resolvePositionTarget(owner, schematic, data, null);

        assertEquals(Optional.of(Vec3d.ofCenter(new BlockPos(1, 2, 3))), resolved);
        assertEquals(new BlockPos(1, 2, 3), data.targetBlockPos);
        assertEquals("house", data.schematicName);
    }

    @Test
    void placeTargetDefinitionStoresBlockId() {
        Node owner = new Node(NodeType.PLACE, 0, 0);
        Node placeTarget = new Node(NodeType.PARAM_PLACE_TARGET, 0, 0);
        placeTarget.getParameter("Block").setStringValue("stone");
        placeTarget.getParameter("X").setStringValue("4");
        placeTarget.getParameter("Y").setStringValue("5");
        placeTarget.getParameter("Z").setStringValue("6");
        RuntimeParameterData data = new RuntimeParameterData();

        Optional<Vec3d> resolved = NodeBehaviorDefinitionRegistry
            .get(NodeType.PARAM_PLACE_TARGET)
            .resolvePositionTarget(owner, placeTarget, data, null);

        assertEquals(Optional.of(Vec3d.ofCenter(new BlockPos(4, 5, 6))), resolved);
        assertEquals(new BlockPos(4, 5, 6), data.targetBlockPos);
        assertEquals("stone", data.targetBlockId);
    }

    @Test
    void directionDefinitionExportsOrientationAliasesAndComparableString() {
        Node owner = new Node(NodeType.CONTROL_IF, 0, 0);
        Node direction = new Node(NodeType.PARAM_DIRECTION, 0, 0);
        direction.setDirectionModeExact(false);
        direction.getParameter("Direction").setStringValue("north");

        Map<String, String> values = direction.exportParameterValues();
        Optional<String> comparable = NodeBehaviorDefinitionRegistry
            .get(NodeType.PARAM_DIRECTION)
            .resolveComparableString(owner, direction);

        assertEquals("cardinal", values.get("Mode"));
        assertEquals("180.0", values.get("Yaw"));
        assertEquals("north", values.get("Side"));
        assertEquals(Optional.of("180.0 0.0"), comparable);
    }

    @Test
    void namedDirectionMappingPreservesExpectedOrientation() {
        NodeBehaviorDefinitionRegistry.Orientation up =
            NodeBehaviorDefinitionRegistry.applyDirection("up", 42.0F, 10.0F);
        NodeBehaviorDefinitionRegistry.Orientation west =
            NodeBehaviorDefinitionRegistry.applyDirection("west", 1.0F, 2.0F);
        NodeBehaviorDefinitionRegistry.Orientation unknown =
            NodeBehaviorDefinitionRegistry.applyDirection("sideways", 12.0F, 34.0F);

        assertEquals(42.0F, up.yaw);
        assertEquals(-90.0F, up.pitch);
        assertEquals(90.0F, west.yaw);
        assertEquals(0.0F, west.pitch);
        assertEquals(12.0F, unknown.yaw);
        assertEquals(34.0F, unknown.pitch);
    }

    @Test
    void amountDefinitionExportsAliasesAndComparableNumber() {
        Node owner = new Node(NodeType.CONTROL_IF, 0, 0);
        Node amount = new Node(NodeType.PARAM_AMOUNT, 0, 0);
        amount.getParameter("Amount").setStringValue("12.5");
        NodeBehaviorDefinition definition = NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_AMOUNT);

        Map<String, String> values = amount.exportParameterValues();
        Optional<Double> comparable = definition.resolveComparableNumber(owner, amount);

        assertEquals("12.5", values.get("Count"));
        assertEquals("12.5", values.get("Threshold"));
        assertEquals("12.5", values.get("Value"));
        assertEquals(Optional.of(12.5), comparable);
    }

    @Test
    void durationDefinitionExportsSecondsAliases() {
        Node duration = new Node(NodeType.PARAM_DURATION, 0, 0);
        duration.setMode(NodeMode.WAIT_MINUTES);
        duration.getParameter("Duration").setStringValue("2");

        Map<String, String> values = duration.exportParameterValues();

        assertEquals("120.0", values.get("Duration"));
        assertEquals("120.0", values.get("IntervalSeconds"));
        assertEquals("120.0", values.get("WaitSeconds"));
        assertEquals("120.0", values.get("DurationSeconds"));
    }

    @Test
    void booleanDefinitionExportsResolvedToggleAliases() {
        Node bool = new Node(NodeType.PARAM_BOOLEAN, 0, 0);
        bool.setBooleanModeLiteral(true);
        bool.getParameter("Toggle").setStringValue("false");

        Map<String, String> values = bool.exportParameterValues();

        assertEquals("literal", values.get("Mode"));
        assertEquals("false", values.get("Toggle"));
        assertEquals("false", values.get("Active"));
        assertEquals("false", values.get("Enabled"));
    }

    @Test
    void inventorySlotDefinitionExportsSlotAliases() {
        Node slot = new Node(NodeType.PARAM_INVENTORY_SLOT, 0, 0);
        slot.getParameter("Slot").setStringValue("3");

        Map<String, String> values = slot.exportParameterValues();

        assertEquals("3", values.get("Slot"));
        assertEquals("3", values.get("SourceSlot"));
        assertEquals("3", values.get("TargetSlot"));
        assertEquals("3", values.get("FirstSlot"));
        assertEquals("3", values.get("SecondSlot"));
    }

    @Test
    void messageDefinitionExportsAliasAndComparableString() {
        Node owner = new Node(NodeType.CONTROL_IF, 0, 0);
        Node message = new Node(NodeType.PARAM_MESSAGE, 0, 0);
        message.getParameter("Text").setStringValue("hello");
        NodeBehaviorDefinition definition = NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_MESSAGE);

        Map<String, String> values = message.exportParameterValues();

        assertEquals("hello", values.get("Message"));
        assertEquals(Optional.of("hello"), definition.resolveComparableString(owner, message));
    }

    @Test
    void guiDefinitionExportsSelectionAliases() {
        Node gui = new Node(NodeType.PARAM_GUI, 0, 0);
        gui.getParameter("GUI").setStringValue("player_inventory");

        Map<String, String> values = gui.exportParameterValues();

        assertEquals("player_inventory", values.get("GUI"));
        assertEquals("player_inventory", values.get("Mode"));
        assertEquals("player_inventory", values.get("GuiMode"));
        assertEquals("player_inventory", values.get("Selection"));
    }

    @Test
    void keyDefinitionExportsInputAliasesAndComparableString() {
        Node owner = new Node(NodeType.CONTROL_IF, 0, 0);
        Node key = new Node(NodeType.PARAM_KEY, 0, 0);
        key.getParameter("Key").setStringValue("GLFW_KEY_A");
        NodeBehaviorDefinition definition = NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_KEY);

        Map<String, String> values = key.exportParameterValues();

        assertEquals("GLFW_KEY_A", values.get("Key"));
        assertEquals("GLFW_KEY_A", values.get("Button"));
        assertEquals("GLFW_KEY_A", values.get("Input"));
        assertEquals(Optional.of("GLFW_KEY_A"), definition.resolveComparableString(owner, key));
    }

    @Test
    void mouseButtonDefinitionExportsInputAliasesAndComparableString() {
        Node owner = new Node(NodeType.CONTROL_IF, 0, 0);
        Node mouseButton = new Node(NodeType.PARAM_MOUSE_BUTTON, 0, 0);
        mouseButton.getParameter("MouseButton").setStringValue("Right");
        NodeBehaviorDefinition definition = NodeBehaviorDefinitionRegistry.get(NodeType.PARAM_MOUSE_BUTTON);

        Map<String, String> values = mouseButton.exportParameterValues();

        assertEquals("Right", values.get("MouseButton"));
        assertEquals("Right", values.get("Button"));
        assertEquals("Right", values.get("Input"));
        assertEquals(Optional.of("Right"), definition.resolveComparableString(owner, mouseButton));
    }

    @Test
    void distanceDefinitionResolvesComparableNumber() {
        Node owner = new Node(NodeType.CONTROL_IF, 0, 0);
        Node distance = new Node(NodeType.PARAM_DISTANCE, 0, 0);
        distance.getParameter("Distance").setStringValue("7.25");

        Optional<Double> comparable = NodeBehaviorDefinitionRegistry
            .get(NodeType.PARAM_DISTANCE)
            .resolveComparableNumber(owner, distance);

        assertEquals(Optional.of(7.25), comparable);
    }

    @Test
    void playerSearchFailureMessageUsesPlayerSelectionKind() {
        Node owner = new Node(NodeType.LOOK, 0, 0);

        assertEquals("No players nearby for pathmind.node.type.look.",
            NodeBehaviorDefinitionRegistry.playerSearchFailureMessage(owner, "Any"));
        assertEquals("Local player unavailable for pathmind.node.type.look.",
            NodeBehaviorDefinitionRegistry.playerSearchFailureMessage(owner, "Self"));
        assertEquals("Player \"Alex\" is not nearby for pathmind.node.type.look.",
            NodeBehaviorDefinitionRegistry.playerSearchFailureMessage(owner, "Alex"));
    }

    @Test
    void noNearbyEntityMessageUsesOwnerDisplayName() {
        Node owner = new Node(NodeType.LOOK, 0, 0);

        assertEquals("No nearby entity found for pathmind.node.type.look.",
            NodeBehaviorDefinitionRegistry.noNearbyEntityMessage(owner));
    }

    @Test
    void itemSearchFailureMessagesUseOwnerDisplayName() {
        Node owner = new Node(NodeType.LOOK, 0, 0);

        assertEquals("Unknown item \"missing_item\" for pathmind.node.type.look.",
            NodeBehaviorDefinitionRegistry.unknownItemMessage(owner, "missing_item"));
        assertEquals("No dropped diamond, emerald found for pathmind.node.type.look.",
            NodeBehaviorDefinitionRegistry.noDroppedItemMessage(owner, java.util.List.of("diamond", "emerald")));
    }

    @Test
    void blockSearchFailureMessagesUseOwnerDisplayName() {
        Node owner = new Node(NodeType.LOOK, 0, 0);

        assertEquals("No blocks defined on parameter for pathmind.node.type.look.",
            NodeBehaviorDefinitionRegistry.noBlocksDefinedMessage(owner));
        assertEquals("No nearby block found for pathmind.node.type.look.",
            NodeBehaviorDefinitionRegistry.noNearbyBlockMessage(owner));
        assertEquals("No matching block from parameter found for pathmind.node.type.look.",
            NodeBehaviorDefinitionRegistry.noMatchingBlockMessage(owner));
        assertEquals("No open block found within range for pathmind.node.type.look.",
            NodeBehaviorDefinitionRegistry.noOpenBlockMessage(owner));
    }
}
