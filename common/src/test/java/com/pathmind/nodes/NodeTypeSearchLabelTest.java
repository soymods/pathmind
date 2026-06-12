package com.pathmind.nodes;

import com.pathmind.ui.sidebar.Sidebar;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NodeTypeSearchLabelTest {

    private static final Pattern LANG_ENTRY_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");

    @Test
    void draggableNodeLabelsAreUniqueInEnglishSearch() throws Exception {
        Map<String, String> translations = loadEnglishTranslations();
        Map<String, NodeType> labelsByNormalizedName = new HashMap<>();
        Sidebar sidebar = new Sidebar(true, true);

        for (NodeType nodeType : NodeType.values()) {
            if (!sidebar.isNodeAvailable(nodeType)) {
                continue;
            }

            String label = getSearchLabel(nodeType, translations);
            String normalizedLabel = label.trim().toLowerCase(Locale.ROOT);
            NodeType existing = labelsByNormalizedName.putIfAbsent(normalizedLabel, nodeType);
            assertEquals(
                null,
                existing,
                () -> "Duplicate searchable node label \"" + label + "\" for " + existing + " and " + nodeType
            );
        }
    }

    @Test
    void unavailableDependencyNodesAreExcludedFromSidebarAvailability() {
        Sidebar sidebarWithoutDependencies = new Sidebar(false, false);

        for (NodeType nodeType : NodeType.values()) {
            if (nodeType == null) {
                continue;
            }
            if (nodeType.requiresBaritone() || nodeType.requiresUiUtils()) {
                assertEquals(
                    false,
                    sidebarWithoutDependencies.isNodeAvailable(nodeType),
                    () -> "Expected dependency-gated node to be hidden: " + nodeType
                );
            }
        }
    }

    private static String getSearchLabel(NodeType nodeType, Map<String, String> translations) throws Exception {
        if (nodeType == NodeType.DROP_SLOT) {
            return requireTranslation(translations, "pathmind.node.type.dropItem");
        }
        Field translationKeyField = NodeType.class.getDeclaredField("translationKey");
        translationKeyField.setAccessible(true);
        String translationKey = (String) translationKeyField.get(nodeType);
        return requireTranslation(translations, translationKey);
    }

    private static String requireTranslation(Map<String, String> translations, String key) {
        String value = translations.get(key);
        assertNotNull(value, () -> "Missing translation for " + key);
        return value;
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
