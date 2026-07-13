package com.pathmind.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    void displayMetadataKeysExistInEnglishTranslations() throws IOException {
        Map<String, String> translations = loadEnglishTranslations();
        for (NodeType type : NodeType.values()) {
            NodeCatalog.NodeDefinition definition = NodeCatalog.definition(type);
            assertNotNull(translations.get(definition.nameKey()), () -> "Missing name translation for " + type + ": " + definition.nameKey());
            assertNotNull(translations.get(definition.descriptionKey()), () -> "Missing description translation for " + type + ": " + definition.descriptionKey());
        }
    }

    @Test
    void graphColorDefaultsToIntrinsicCategoryUnlessExplicitlyPreserved() {
        assertEquals(NodeCategory.NAVIGATION.getColor(), NodeType.TRAVEL.getColor());
        assertEquals(NodeCategory.PLAYER.getColor(), NodeType.WALK.getColor());
        assertEquals(NodeCatalog.definition(NodeType.START).baseColor(), NodeType.START.getColor());
        assertEquals(NodeCatalog.definition(NodeType.CUSTOM_NODE).baseColor(), NodeType.CUSTOM_NODE.getColor());
    }

    @Test
    void graphColorCanFollowVisibleSidebarPlacement() {
        assertEquals(NodeCategory.PLAYER.getColor(), NodeCatalog.graphColor(NodeType.TRAVEL, false, true));
        assertEquals(NodeCategory.NAVIGATION.getColor(), NodeCatalog.graphColor(NodeType.TRAVEL, true, true));
        assertEquals(NodeCatalog.definition(NodeType.START).baseColor(), NodeCatalog.graphColor(NodeType.START, false, true));
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

    private static Map<String, String> loadEnglishTranslations() throws IOException {
        Path langPath = Path.of("src/main/resources/assets/pathmind/lang/en_us.json");
        String json = Files.readString(langPath);
        Map<String, String> translations = new HashMap<>();
        Matcher matcher = LANG_ENTRY_PATTERN.matcher(json);
        while (matcher.find()) {
            translations.put(matcher.group(1), matcher.group(2));
        }
        return translations;
    }
}
