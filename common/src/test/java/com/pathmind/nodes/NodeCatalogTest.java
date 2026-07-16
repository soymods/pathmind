package com.pathmind.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

import org.junit.jupiter.api.Test;

class NodeCatalogTest {
    private static final Pattern LANG_ENTRY_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");

    @Test
    void everyNodeTypeHasOneCatalogDefinition() {
        for (NodeType type : NodeType.values()) {
            NodeCatalog.NodeDefinition definition = NodeCatalog.definition(type);
            assertNotNull(definition, () -> "Missing catalog definition for " + type);
            assertFalse(definition.nameKey().isBlank(), () -> "Missing name key for " + type);
            assertFalse(definition.descriptionKey().isBlank(), () -> "Missing description key for " + type);
        }
    }

    @Test
    void displayMetadataComesFromCatalog() {
        assertEquals("pathmind.node.type.travel", NodeCatalog.definition(NodeType.TRAVEL).nameKey());
        assertEquals("pathmind.node.type.travel.desc", NodeCatalog.definition(NodeType.TRAVEL).descriptionKey());
        assertEquals("pathmind.node.type.dropItem", NodeCatalog.definition(NodeType.DROP_SLOT).nameKey());
        assertEquals("pathmind.node.type.dropItem.desc", NodeCatalog.definition(NodeType.DROP_SLOT).descriptionKey());
    }

    @Test
    void runPresetIsVisibleWhileLegacyTemplateNodesStayHidden() {
        assertTrue(NodeCatalog.shouldDisplayInSidebar(NodeType.RUN_PRESET, true, true));
        assertFalse(NodeCatalog.shouldDisplayInSidebar(NodeType.TEMPLATE, true, true));
        assertEquals(NodeCategory.FLOW, NodeCatalog.sidebarPlacement(NodeType.RUN_PRESET, true, true).displayCategory());
    }

    @Test
    void displayMetadataKeysExistInEnglishTranslations() throws IOException {
        Map<String, String> translations = loadEnglishTranslations();
        for (NodeType type : NodeType.values()) {
            NodeCatalog.NodeDefinition definition = NodeCatalog.definition(type);
            assertNotNull(translations.get(definition.nameKey()), () -> "Missing name translation for " + type + ": " + definition.nameKey());
            assertNotNull(translations.get(definition.descriptionKey()), () -> "Missing description translation for " + type + ": " + definition.descriptionKey());
        }
    }

    @Test
    void routineTranslationsExistInEveryLocale() throws IOException {
        Path langDirectory = Path.of("src/main/resources/assets/pathmind/lang");
        Map<String, String> english = loadTranslations(langDirectory.resolve("en_us.json"));
        Set<String> routineKeys = new HashSet<>();
        for (String key : english.keySet()) {
            if (key.startsWith("pathmind.routine.")
                || key.startsWith("pathmind.node.type.routine")
                || key.equals("pathmind.node.category.routines")
                || key.equals("pathmind.node.category.routines.desc")) {
                routineKeys.add(key);
            }
        }

        try (Stream<Path> locales = Files.list(langDirectory)) {
            for (Path locale : locales.filter(path -> path.toString().endsWith(".json")).toList()) {
                Map<String, String> translations = loadTranslations(locale);
                assertTrue(translations.keySet().containsAll(routineKeys),
                    () -> locale.getFileName() + " is missing routine translations: "
                        + routineKeys.stream().filter(key -> !translations.containsKey(key)).toList());
            }
        }
    }

    @Test
    void graphColorDefaultsToIntrinsicCategoryUnlessExplicitlyPreserved() {
        assertEquals(NodeCategory.NAVIGATION.getColor(), NodeType.TRAVEL.getColor());
        assertEquals(NodeCategory.PLAYER.getColor(), NodeType.WALK.getColor());
        assertEquals(NodeCatalog.definition(NodeType.START).baseColor(), NodeType.START.getColor());
        assertEquals(NodeCatalog.definition(NodeType.START_CHAIN).baseColor(), NodeType.START_CHAIN.getColor());
    }

    @Test
    void graphColorCanFollowVisibleSidebarPlacement() {
        assertEquals(NodeCategory.PLAYER.getColor(), NodeCatalog.graphColor(NodeType.TRAVEL, false, true));
        assertEquals(NodeCategory.NAVIGATION.getColor(), NodeCatalog.graphColor(NodeType.TRAVEL, true, true));
        assertEquals(NodeCatalog.definition(NodeType.START).baseColor(), NodeCatalog.graphColor(NodeType.START, false, true));
        assertEquals(NodeCatalog.definition(NodeType.START_CHAIN).baseColor(), NodeCatalog.graphColor(NodeType.START_CHAIN, false, true));
    }

    @Test
    void navigationFallsBackIntoPlayerWhenBaritoneIsUnavailable() {
        assertTrue(NodeCatalog.sidebarGroups(NodeCategory.NAVIGATION, false, true).isEmpty());

        NodeCatalog.NodePlacement travelPlacement = NodeCatalog.sidebarPlacement(NodeType.TRAVEL, false, true);
        assertNotNull(travelPlacement);
        assertEquals(NodeCategory.PLAYER, travelPlacement.displayCategory());
        assertEquals(NodeCategory.PLAYER.getColor(), travelPlacement.displayCategory().getColor());

        assertNull(NodeCatalog.sidebarPlacement(NodeType.GOTO, false, true));

        List<NodeCatalog.SidebarGroup> playerGroups = NodeCatalog.sidebarGroups(NodeCategory.PLAYER, false, true);
        assertTrue(containsNode(playerGroups, NodeType.TRAVEL));
        assertFalse(containsNode(playerGroups, NodeType.GOTO));
    }

    @Test
    void navigationGetsOwnCategoryWhenBaritoneIsAvailable() {
        NodeCatalog.NodePlacement travelPlacement = NodeCatalog.sidebarPlacement(NodeType.TRAVEL, true, true);
        assertNotNull(travelPlacement);
        assertEquals(NodeCategory.NAVIGATION, travelPlacement.displayCategory());

        NodeCatalog.NodePlacement gotoPlacement = NodeCatalog.sidebarPlacement(NodeType.GOTO, true, true);
        assertNotNull(gotoPlacement);
        assertEquals(NodeCategory.NAVIGATION, gotoPlacement.displayCategory());

        List<NodeCatalog.SidebarGroup> playerGroups = NodeCatalog.sidebarGroups(NodeCategory.PLAYER, true, true);
        assertFalse(containsNode(playerGroups, NodeType.TRAVEL));
    }

    @Test
    void startPathCanUseCurrentGoalWithoutAParameterCard() {
        Node path = new Node(NodeType.PATH, 0, 0);

        assertTrue(path.hasParameterSlot());
        assertFalse(path.isParameterSlotRequired(0));
    }

    @Test
    void sidebarLayoutDoesNotDuplicateVisibleNodes() {
        assertNoDuplicates(NodeCatalog.sidebarGroupsForAllCategories(false, true));
        assertNoDuplicates(NodeCatalog.sidebarGroupsForAllCategories(true, true));
    }

    @Test
    void dependencyGatedNodesAreHiddenWhenDependencyIsUnavailable() {
        assertNull(NodeCatalog.sidebarPlacement(NodeType.UI_UTILS, true, false));
        assertNotNull(NodeCatalog.sidebarPlacement(NodeType.UI_UTILS, true, true));
        assertNull(NodeCatalog.sidebarPlacement(NodeType.PARAM_WAYPOINT, false, true));
        assertNotNull(NodeCatalog.sidebarPlacement(NodeType.PARAM_WAYPOINT, true, true));
    }

    @Test
    void catalogOwnsCoreNodeTraits() {
        assertTrue(NodeCatalog.isBooleanSensor(NodeType.SENSOR_IS_DAYTIME));
        assertTrue(NodeCatalog.isBooleanSensor(NodeType.OPERATOR_EQUALS));
        assertFalse(NodeCatalog.isBooleanSensor(NodeType.SENSOR_POSITION_OF));

        assertTrue(NodeCatalog.isParameterNode(NodeType.PARAM_BLOCK));
        assertTrue(NodeCatalog.isParameterNode(NodeType.SENSOR_POSITION_OF));
        assertTrue(NodeCatalog.isParameterNode(NodeType.VARIABLE));
        assertFalse(NodeCatalog.isParameterNode(NodeType.WALK));

        assertTrue(NodeCatalog.usesMinimalNodePresentation(NodeType.CROUCH));
        assertTrue(NodeCatalog.usesMinimalNodePresentation(NodeType.OPERATOR_GREATER));
        assertFalse(NodeCatalog.usesMinimalNodePresentation(NodeType.MESSAGE));

        assertTrue(NodeCatalog.shouldRenderInlineParameters(NodeType.UI_UTILS));
        assertTrue(NodeCatalog.hasBooleanToggle(NodeType.SENSOR_IS_RAINING));
        assertTrue(NodeCatalog.hasPopupEditButton(NodeType.PARAM_INVENTORY_SLOT));
        assertFalse(NodeCatalog.hasPopupEditButton(NodeType.PARAM_BOOLEAN));
    }

    @Test
    void catalogOwnsParameterTraitSchema() {
        assertEquals(EnumSet.of(NodeValueTrait.NUMBER), NodeCatalog.providedTraits(NodeType.CHANGE_VARIABLE));
        assertEquals(EnumSet.of(NodeValueTrait.NUMBER), NodeCatalog.providedTraits(NodeType.SENSOR_FIND_TRADE));
        assertEquals(EnumSet.of(NodeValueTrait.DIRECTION), NodeCatalog.providedTraits(NodeType.PARAM_BLOCK_FACE));

        assertFalse(NodeCatalog.canHostParameter(NodeType.CHANGE_VARIABLE));
        assertEquals(0, NodeCatalog.parameterSlotCount(NodeType.CHANGE_VARIABLE));

        assertEquals(2, NodeCatalog.parameterSlotCount(NodeType.WALK));
        assertEquals("Direction", NodeCatalog.parameterSlotLabel(NodeType.WALK, 0));
        assertEquals(EnumSet.of(NodeValueTrait.DURATION, NodeValueTrait.DISTANCE), NodeCatalog.acceptedTraits(NodeType.WALK, 1));

        assertEquals("Item", NodeCatalog.parameterSlotLabel(NodeType.SENSOR_FIND_TRADE, 0));
        assertEquals(EnumSet.of(NodeValueTrait.ITEM), NodeCatalog.acceptedTraits(NodeType.SENSOR_FIND_TRADE, 0));
        assertEquals(EnumSet.of(NodeValueTrait.GUI), NodeCatalog.acceptedTraits(NodeType.SENSOR_GUI_FILLED, 0));
    }

    @Test
    void catalogOwnsDefaultParameterTemplates() {
        assertTrue(NodeCatalog.hasParameterDefinitions(NodeType.PRESS_KEY));
        assertTrue(parameterIds(NodeType.PRESS_KEY).contains("duration"));
        assertTrue(parameterIds(NodeType.PRESS_KEY).contains("useamount"));

        assertEquals(List.of("changevariableamount", "changevariableoperation"), parameterIds(NodeType.CHANGE_VARIABLE));
        assertTrue(parameterIds(NodeType.PARAM_DIRECTION).contains("directionyawoffset"));
        assertTrue(parameterIds(NodeType.PARAM_DIRECTION).contains("directionpitchoffset"));

        List<NodeParameter> modeParameters = new ArrayList<>();
        NodeCatalog.initializeParameters(modeParameters, NodeType.GOTO, NodeMode.GOTO_XYZ);
        assertEquals(List.of("x", "y", "z"), modeParameters.stream().map(NodeParameter::getId).toList());
    }

    @Test
    void catalogOwnsExecutionRoutes() {
        assertEquals(NodeCatalog.ExecutionRoute.SENSOR_EVALUATION, NodeCatalog.executionRoute(NodeType.SENSOR_GUI_FILLED));
        assertEquals(NodeCatalog.ExecutionRoute.SENSOR_EVALUATION, NodeCatalog.executionRoute(NodeType.SENSOR_FIND_TRADE));
        assertEquals(NodeCatalog.ExecutionRoute.RUN_PRESET, NodeCatalog.executionRoute(NodeType.RUN_PRESET));
        assertEquals(NodeCatalog.ExecutionRoute.REMOVE_LIST_ITEM, NodeCatalog.executionRoute(NodeType.REMOVE_LIST_ITEM));

        assertTrue(NodeCommandDispatcher.hasExplicitRoute(NodeType.SENSOR_FIND_TRADE));
        assertFalse(NodeCommandDispatcher.hasExplicitRoute(NodeType.PARAM_BLOCK));
        assertNull(NodeCatalog.executionRoute(NodeType.PARAM_BLOCK));
    }

    private static boolean containsNode(List<NodeCatalog.SidebarGroup> groups, NodeType type) {
        for (NodeCatalog.SidebarGroup group : groups) {
            if (group.nodes().contains(type)) {
                return true;
            }
        }
        return false;
    }

    private static void assertNoDuplicates(List<NodeCatalog.SidebarGroup> groups) {
        Set<NodeType> seen = EnumSet.noneOf(NodeType.class);
        Set<NodeType> duplicates = new HashSet<>();
        for (NodeCatalog.SidebarGroup group : groups) {
            for (NodeType type : group.nodes()) {
                if (!seen.add(type)) {
                    duplicates.add(type);
                }
            }
        }
        assertTrue(duplicates.isEmpty(), () -> "Duplicate sidebar nodes: " + duplicates);
    }

    private static List<String> parameterIds(NodeType type) {
        List<NodeParameter> parameters = new ArrayList<>();
        NodeCatalog.initializeParameters(parameters, type, null);
        return parameters.stream().map(NodeParameter::getId).toList();
    }

    private static Map<String, String> loadEnglishTranslations() throws IOException {
        return loadTranslations(Path.of("src/main/resources/assets/pathmind/lang/en_us.json"));
    }

    private static Map<String, String> loadTranslations(Path langPath) throws IOException {
        String json = Files.readString(langPath);
        Map<String, String> translations = new HashMap<>();
        Matcher matcher = LANG_ENTRY_PATTERN.matcher(json);
        while (matcher.find()) {
            translations.put(matcher.group(1), matcher.group(2));
        }
        return translations;
    }
}
