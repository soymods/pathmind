package com.pathmind.validation;

import com.pathmind.data.PresetManager;
import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeMode;
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

    @Test
    void validateDoesNotWarnWhenCustomNodePresetExistsButInternalGraphCacheIsEmpty() throws Exception {
        Files.writeString(PresetManager.getPresetPath(PRESET_NAME), """
            {
              "nodes": [
                {
                  "id": "start",
                  "type": "START",
                  "x": 0,
                  "y": 0,
                  "parameters": [],
                  "parameterAttachments": [],
                  "startNodeNumber": 1
                }
              ],
              "connections": []
            }
            """);

        Node start = new Node(NodeType.START, 0, 0);
        Node customNode = new Node(NodeType.CUSTOM_NODE, 100, 0);
        customNode.getParameter("Preset").setStringValue(PRESET_NAME);
        customNode.setTemplateGraphData(null);
        NodeConnection connection = new NodeConnection(start, customNode, 0, 0);

        GraphValidationResult result = GraphValidator.validate(
            List.of(start, customNode),
            List.of(connection),
            PresetManager.getDefaultPresetName(),
            true,
            true
        );

        assertFalse(hasIssueCode(result, "missing_template_graph"));
    }

    @Test
    void validateAllowsDistinctJoinInputs() {
        Node startOne = new Node(NodeType.START, 0, 0);
        Node startTwo = new Node(NodeType.START, 0, 80);
        Node joinAny = new Node(NodeType.CONTROL_JOIN_ANY, 120, 40);
        Node wait = new Node(NodeType.WAIT, 240, 40);

        GraphValidationResult result = GraphValidator.validate(
            List.of(startOne, startTwo, joinAny, wait),
            List.of(
                new NodeConnection(startOne, joinAny, 0, 0),
                new NodeConnection(startTwo, joinAny, 0, 1),
                new NodeConnection(joinAny, wait, 0, 0)
            ),
            PresetManager.getDefaultPresetName(),
            true,
            true
        );

        assertFalse(result.hasErrors());
    }

    @Test
    void validateWarnsWhenVariableAssignmentsAreIncompatibleWithAttachedHost() {
        Node start = new Node(NodeType.START, 0, 0);
        Node setVariable = new Node(NodeType.SET_VARIABLE, 100, 0);
        Node look = new Node(NodeType.LOOK, 220, 0);
        NodeConnection first = new NodeConnection(start, setVariable, 0, 0);
        NodeConnection second = new NodeConnection(setVariable, look, 0, 0);

        Node variableTarget = new Node(NodeType.VARIABLE, 0, 0);
        variableTarget.getParameter("Variable").setStringValue("stored_target");
        Node itemValue = new Node(NodeType.PARAM_MESSAGE, 0, 0);
        itemValue.getParameter("Text").setStringValue("hello");
        Node variableConsumer = new Node(NodeType.VARIABLE, 0, 0);
        variableConsumer.getParameter("Variable").setStringValue("stored_target");

        assertTrue(setVariable.attachParameter(variableTarget, 0));
        assertTrue(setVariable.attachParameter(itemValue, 1));
        assertTrue(look.attachParameter(variableConsumer, 0));

        GraphValidationResult result = GraphValidator.validate(
            List.of(start, setVariable, look, variableTarget, itemValue, variableConsumer),
            List.of(first, second),
            PresetManager.getDefaultPresetName(),
            true,
            true
        );

        assertTrue(hasIssueCode(result, "variable_type_mismatch"));
        assertTrue(result.hasWarnings());
    }

    @Test
    void validateDoesNotWarnWhenVariableAssignmentsMatchAttachedHost() {
        Node start = new Node(NodeType.START, 0, 0);
        Node setVariable = new Node(NodeType.SET_VARIABLE, 100, 0);
        Node look = new Node(NodeType.LOOK, 220, 0);
        NodeConnection first = new NodeConnection(start, setVariable, 0, 0);
        NodeConnection second = new NodeConnection(setVariable, look, 0, 0);

        Node variableTarget = new Node(NodeType.VARIABLE, 0, 0);
        variableTarget.getParameter("Variable").setStringValue("stored_target");
        Node lookValue = new Node(NodeType.SENSOR_LOOK_DIRECTION, 0, 0);
        lookValue.setMode(NodeMode.SENSOR_LOOK_ROTATION);
        Node variableConsumer = new Node(NodeType.VARIABLE, 0, 0);
        variableConsumer.getParameter("Variable").setStringValue("stored_target");

        assertTrue(setVariable.attachParameter(variableTarget, 0));
        assertTrue(setVariable.attachParameter(lookValue, 1));
        assertTrue(look.attachParameter(variableConsumer, 0));

        GraphValidationResult result = GraphValidator.validate(
            List.of(start, setVariable, look, variableTarget, lookValue, variableConsumer),
            List.of(first, second),
            PresetManager.getDefaultPresetName(),
            true,
            true
        );

        assertFalse(hasIssueCode(result, "variable_type_mismatch"));
    }

    private boolean hasIssueCode(GraphValidationResult result, String code) {
        return result.getIssues().stream().anyMatch(issue -> issue != null && code.equals(issue.getCode()));
    }
}
