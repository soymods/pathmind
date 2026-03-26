package com.pathmind.validation;

import com.pathmind.data.PresetManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphValidatorTest {

    private static final String PRESET_NAME = "GraphValidatorTestPreset";

    @AfterEach
    void cleanupPreset() throws Exception {
        Files.deleteIfExists(PresetManager.getPresetPath(PRESET_NAME));
    }

    @Test
    void validateReportsMissingStartNode() {
        Node wait = new Node(NodeType.WAIT, 0, 0);

        GraphValidationResult result = GraphValidator.validate(
            List.of(wait),
            List.of(),
            PresetManager.getDefaultPresetName(),
            true,
            true
        );

        assertTrue(hasIssueCode(result, "missing_start"));
        assertTrue(result.hasErrors());
    }

    @Test
    void validateReportsDuplicateEventFunctionNames() {
        Node start = new Node(NodeType.START, 0, 0);
        Node first = new Node(NodeType.EVENT_FUNCTION, 100, 0);
        Node second = new Node(NodeType.EVENT_FUNCTION, 200, 0);
        first.getParameter("Name").setStringValue("tick");
        second.getParameter("Name").setStringValue("tick");

        GraphValidationResult result = GraphValidator.validate(
            List.of(start, first, second),
            List.of(),
            PresetManager.getDefaultPresetName(),
            true,
            true
        );

        assertTrue(hasIssueCode(result, "duplicate_event_function"));
    }

    @Test
    void validateReportsMissingRequiredParameterSlot() {
        Node start = new Node(NodeType.START, 0, 0);
        Node place = new Node(NodeType.PLACE, 100, 0);
        NodeConnection connection = new NodeConnection(start, place, 0, 0);

        GraphValidationResult result = GraphValidator.validate(
            List.of(start, place),
            List.of(connection),
            PresetManager.getDefaultPresetName(),
            true,
            true
        );

        assertTrue(hasIssueCode(result, "missing_parameter_slot"));
    }

    @Test
    void validateReportsMissingPresetTargetForRunPreset() {
        Node start = new Node(NodeType.START, 0, 0);
        Node runPreset = new Node(NodeType.RUN_PRESET, 100, 0);
        runPreset.getParameter("Preset").setStringValue("DefinitelyMissingPreset");
        NodeConnection connection = new NodeConnection(start, runPreset, 0, 0);

        GraphValidationResult result = GraphValidator.validate(
            List.of(start, runPreset),
            List.of(connection),
            PresetManager.getDefaultPresetName(),
            true,
            true
        );

        assertTrue(hasIssueCode(result, "missing_preset"));
    }

    @Test
    void validateAcceptsExistingPresetReference() throws Exception {
        Files.writeString(PresetManager.getPresetPath(PRESET_NAME), "{}");

        Node start = new Node(NodeType.START, 0, 0);
        Node runPreset = new Node(NodeType.RUN_PRESET, 100, 0);
        runPreset.getParameter("Preset").setStringValue(PRESET_NAME);
        NodeConnection connection = new NodeConnection(start, runPreset, 0, 0);

        GraphValidationResult result = GraphValidator.validate(
            List.of(start, runPreset),
            List.of(connection),
            PresetManager.getDefaultPresetName(),
            true,
            true
        );

        assertFalse(hasIssueCode(result, "missing_preset"));
    }

    private boolean hasIssueCode(GraphValidationResult result, String code) {
        return result.getIssues().stream().anyMatch(issue -> issue != null && code.equals(issue.getCode()));
    }
}
