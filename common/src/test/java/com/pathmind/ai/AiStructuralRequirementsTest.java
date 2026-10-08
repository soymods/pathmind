package com.pathmind.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiStructuralRequirementsTest {
    private static JsonArray requirements(String json) {
        JsonArray array = new JsonArray();
        array.add(JsonParser.parseString(json).getAsJsonObject());
        return array;
    }

    @Test void rejectsAFlowOutOfANodeTypeThatHasNoOutputSockets() {
        // CONTROL_FOREVER hosts its body as an attachment and exposes no output socket, so this
        // requirement can never be satisfied by apply_graph_commands. Accepting it strands the run:
        // validate_graph reports it forever and a correction cannot drop a recorded requirement.
        var failure = assertThrows(IllegalArgumentException.class, () -> AiStructuralRequirements.checkShape(
            requirements("""
                {"kind":"flow","ref":"mineLoop","toRef":"diamondCheck",
                 "nodeType":"CONTROL_FOREVER","outputSocket":0,"inputSocket":0}
                """)));
        assertTrue(failure.getMessage().contains("CONTROL_FOREVER"), failure.getMessage());
        assertTrue(failure.getMessage().contains("action"), failure.getMessage());
    }

    @Test void rejectsASocketBeyondWhatTheNodeTypeExposes() {
        var failure = assertThrows(IllegalArgumentException.class, () -> AiStructuralRequirements.checkShape(
            requirements("""
                {"kind":"flow","ref":"branch","toRef":"next",
                 "nodeType":"CONTROL_IF_ELSE","outputSocket":2,"inputSocket":0}
                """)));
        assertTrue(failure.getMessage().contains("CONTROL_IF_ELSE"), failure.getMessage());
    }

    @Test void acceptsSocketsTheNodeTypeActuallyExposes() {
        assertDoesNotThrow(() -> AiStructuralRequirements.checkShape(requirements("""
            {"kind":"flow","ref":"branch","toRef":"next",
             "nodeType":"CONTROL_IF_ELSE","outputSocket":1,"inputSocket":0}
            """)));
        assertDoesNotThrow(() -> AiStructuralRequirements.checkShape(requirements("""
            {"kind":"flow","ref":"start","toRef":"mineLoop",
             "nodeType":"START","outputSocket":0,"inputSocket":0}
            """)));
    }

    @Test void leavesNonFlowRequirementsAlone() {
        assertDoesNotThrow(() -> AiStructuralRequirements.checkShape(requirements("""
            {"kind":"action","ref":"mineLoop","toRef":"diamondCheck","nodeType":"CONTROL_FOREVER"}
            """)));
    }
}
