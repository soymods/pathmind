package com.pathmind.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pathmind.data.SettingsManager;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class NodeAlertTest {

    @Test
    void jsonStringEscapesCharactersThatWouldBreakTheDiscordPayload() {
        // A message containing a quote or a newline is ordinary user text, and unescaped it
        // produces a malformed body that Discord rejects with a 400.
        assertEquals("\"plain\"", NodeAlertCommandExecutor.jsonString("plain"));
        assertEquals("\"say \\\"hi\\\"\"", NodeAlertCommandExecutor.jsonString("say \"hi\""));
        assertEquals("\"a\\\\b\"", NodeAlertCommandExecutor.jsonString("a\\b"));
        assertEquals("\"line1\\nline2\"", NodeAlertCommandExecutor.jsonString("line1\nline2"));
        assertEquals("\"tab\\there\"", NodeAlertCommandExecutor.jsonString("tab\there"));
        assertEquals("\"\"", NodeAlertCommandExecutor.jsonString(null));
    }

    @Test
    void jsonStringEscapesControlCharactersAsUnicode() {
        assertEquals("\"\\u0007\"", NodeAlertCommandExecutor.jsonString("\u0007"));
    }

    @Test
    void jsonStringLeavesNonAsciiTextIntact() {
        // The body is sent as UTF-8, so accented and non-Latin text must survive unescaped.
        assertEquals("\"raid finí\"", NodeAlertCommandExecutor.jsonString("raid finí"));
    }

    @Test
    void webhookUrlAcceptsHttpsEndpoints() {
        assertNotNull(SettingsManager.sanitizeWebhookUrl("https://ntfy.sh/my-topic"));
        assertNotNull(SettingsManager.sanitizeWebhookUrl(
            "https://discord.com/api/webhooks/123/abc"));
        assertEquals("https://ntfy.sh/t",
            SettingsManager.sanitizeWebhookUrl("  https://ntfy.sh/t  "));
    }

    @Test
    void webhookUrlRejectsAnythingThatIsNotHttps() {
        // This string comes from the user and drives an outbound request from the game client,
        // so every non-https scheme has to be refused rather than merely discouraged.
        List<String> rejected = Arrays.asList(
            null,
            "",
            "   ",
            "http://ntfy.sh/my-topic",
            "ftp://example.com/hook",
            "file:///C:/windows/system32",
            "javascript:alert(1)",
            "not a url at all",
            "https://",
            "://missing-scheme");
        for (String candidate : rejected) {
            assertNull(SettingsManager.sanitizeWebhookUrl(candidate),
                "expected rejection for: " + candidate);
        }
    }

    @Test
    void alertIsRegisteredAsARoutedInterfaceNode() {
        assertTrue(NodeCatalog.hasExecutionRoute(NodeType.ALERT));
        assertEquals(NodeCategory.INTERFACE, NodeCatalog.category(NodeType.ALERT));
        assertEquals(NodeMode.ALERT_SOUND, NodeMode.getDefaultModeForNodeType(NodeType.ALERT));
        assertEquals(2, NodeMode.getModesForNodeType(NodeType.ALERT).length);
    }
}
