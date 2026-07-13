package com.pathmind.data;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeMode;
import com.pathmind.nodes.NodeType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Installs the bundled learning presets for new Pathmind workspaces.
 */
public final class OnboardingPresetManager {
    private static final String INSTALL_MARKER_FILE_NAME = "example_presets_installed.txt";
    private static final String PRESETS_DIRECTORY_NAME = "presets";

    private OnboardingPresetManager() {
    }

    public static void ensureExamplePresetsInstalled() {
        Path baseDirectory = PresetManager.getBaseDirectory();
        Path markerPath = baseDirectory.resolve(INSTALL_MARKER_FILE_NAME);
        if (Files.exists(markerPath)) {
            return;
        }
        RestoreResult result = restoreExamplePresets(baseDirectory, false);
        if (!result.success()) {
            return;
        }
        try {
            Files.writeString(markerPath, "1", StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to mark example presets as installed: " + e.getMessage());
        }
    }

    public static RestoreResult restoreExamplePresets() {
        return restoreExamplePresets(PresetManager.getBaseDirectory(), true);
    }

    static RestoreResult restoreExamplePresets(Path baseDirectory, boolean overwriteExisting) {
        if (baseDirectory == null) {
            return new RestoreResult(false, 0, List.of());
        }
        Path presetsDirectory = baseDirectory.resolve(PRESETS_DIRECTORY_NAME);
        try {
            Files.createDirectories(presetsDirectory);
        } catch (IOException e) {
            System.err.println("Failed to create example preset directory: " + e.getMessage());
            return new RestoreResult(false, 0, List.of());
        }

        int restoredCount = 0;
        List<String> restoredNames = new ArrayList<>();
        for (ExamplePreset preset : createExamplePresets()) {
            Path presetPath = presetsDirectory.resolve(preset.name() + ".json");
            if (!overwriteExisting && Files.exists(presetPath)) {
                continue;
            }
            if (NodeGraphPersistence.saveNodeGraphToPath(preset.nodes(), preset.connections(), presetPath)) {
                restoredCount++;
                restoredNames.add(preset.name());
            }
        }
        return new RestoreResult(true, restoredCount, restoredNames);
    }

    static List<ExamplePreset> createExamplePresets() {
        return List.of(
            basicsPreset(),
            logicPreset(),
            advancedPreset()
        );
    }

    private static ExamplePreset basicsPreset() {
        Node note = note(20, 20, 280, 112,
            "Start here: this is a simple left-to-right chain. Run it, then change the Message and Wait values.");
        Node start = node(NodeType.START, 70, 170);
        start.setStartNodeNumber(1);
        Node message = message(250, 160, "Hello from Pathmind.", "This preset shows a basic sequence.");
        Node wait = node(NodeType.WAIT, 480, 170);
        wait.setMode(NodeMode.WAIT_SECONDS);
        set(wait, "Duration", "1.5");
        Node walk = node(NodeType.WALK, 680, 170);
        set(walk, "Duration", "0.75");
        set(walk, "Distance", "2.0");
        Node done = message(900, 160, "Done. Try changing the nodes, then run again.");

        List<Node> nodes = List.of(note, start, message, wait, walk, done);
        List<NodeConnection> connections = List.of(
            connection(start, message),
            connection(message, wait),
            connection(wait, walk),
            connection(walk, done)
        );
        return new ExamplePreset("Example 1 - Basics", nodes, connections);
    }

    private static ExamplePreset logicPreset() {
        Node note = note(20, 20, 330, 132,
            "This preset introduces variables, repeat, and if/else. The loop changes $steps, then the branch checks whether it passed 2.");
        Node start = node(NodeType.START, 60, 190);
        start.setStartNodeNumber(1);
        Node setSteps = node(NodeType.SET_VARIABLE, 230, 180);
        Node stepsTarget = variable(230, 285, "steps");
        Node zero = amount(430, 285, "0");
        attach(setSteps, stepsTarget, 0);
        attach(setSteps, zero, 1);

        Node repeat = node(NodeType.CONTROL_REPEAT, 470, 180);
        set(repeat, "Count", "3");
        Node addOne = node(NodeType.CHANGE_VARIABLE, 470, 315);
        Node changeTarget = variable(470, 425, "steps");
        attach(addOne, changeTarget, 0);
        set(addOne, "Operation", "+");
        addOne.setMessageLines(List.of("1"));
        attachAction(repeat, addOne);

        Node branch = node(NodeType.CONTROL_IF_ELSE, 720, 180);
        Node greater = node(NodeType.OPERATOR_GREATER, 720, 315);
        Node readSteps = variable(720, 440, "steps");
        Node threshold = amount(900, 440, "2");
        attach(greater, readSteps, 0);
        attach(greater, threshold, 1);
        attachSensor(branch, greater);

        Node trueMessage = message(990, 120, "True branch: $steps is greater than 2.");
        Node falseMessage = message(990, 260, "False branch: $steps did not pass 2 yet.");

        List<Node> nodes = List.of(note, start, setSteps, stepsTarget, zero, repeat, addOne, changeTarget,
            branch, greater, readSteps, threshold, trueMessage, falseMessage);
        List<NodeConnection> connections = List.of(
            connection(start, setSteps),
            connection(setSteps, repeat),
            connection(repeat, branch),
            connection(branch, trueMessage, 0),
            connection(branch, falseMessage, 1)
        );
        return new ExamplePreset("Example 2 - Variables and If Else", nodes, connections);
    }

    private static ExamplePreset advancedPreset() {
        Node note = note(20, 20, 370, 150,
            "This preset combines $variable text calls, relative coordinates, and list setup. Relative coordinates like ~5 resolve from the player position at runtime.");
        Node start = node(NodeType.START, 60, 210);
        start.setStartNodeNumber(1);
        Node setTargetName = node(NodeType.SET_VARIABLE, 230, 200);
        Node targetName = variable(230, 315, "targetName");
        Node nameValue = text(430, 315, "base camp");
        attach(setTargetName, targetName, 0);
        attach(setTargetName, nameValue, 1);

        Node intro = message(500, 200, "Heading to $targetName.", "The next coordinate uses relative offsets.");
        Node goRelative = node(NodeType.GOTO, 760, 200);
        goRelative.setMode(NodeMode.GOTO_XYZ);
        Node relativeCoordinate = coordinate(760, 330, "~5", "~", "~-2");
        attach(goRelative, relativeCoordinate, 0);

        Node createList = node(NodeType.CREATE_LIST, 1000, 200);
        set(createList, "List", "nearbyTargets");
        set(createList, "UseRadius", "true");
        set(createList, "Radius", "16");
        Node listCenter = coordinate(1000, 335, "~", "~", "~");
        attach(createList, listCenter, 0);

        Node summary = message(1240, 200,
            "Reached $targetName using ~5 ~ ~-2.",
            "Created nearbyTargets for later list nodes.");

        List<Node> nodes = List.of(note, start, setTargetName, targetName, nameValue, intro, goRelative,
            relativeCoordinate, createList, listCenter, summary);
        List<NodeConnection> connections = List.of(
            connection(start, setTargetName),
            connection(setTargetName, intro),
            connection(intro, goRelative),
            connection(goRelative, createList),
            connection(createList, summary)
        );
        return new ExamplePreset("Example 3 - Relative Variables and Lists", nodes, connections);
    }

    private static Node node(NodeType type, int x, int y) {
        return new Node(type, x, y);
    }

    private static Node note(int x, int y, int width, int height, String text) {
        Node node = node(NodeType.STICKY_NOTE, x, y);
        node.setStickyNoteText(text);
        node.setStickyNoteSize(width, height);
        return node;
    }

    private static Node message(int x, int y, String... lines) {
        Node node = node(NodeType.MESSAGE, x, y);
        node.setMessageLines(List.of(lines));
        node.setMessageClientSide(true);
        return node;
    }

    private static Node variable(int x, int y, String name) {
        Node node = node(NodeType.VARIABLE, x, y);
        set(node, "Variable", name);
        return node;
    }

    private static Node amount(int x, int y, String value) {
        Node node = node(NodeType.PARAM_AMOUNT, x, y);
        set(node, "Amount", value);
        return node;
    }

    private static Node text(int x, int y, String value) {
        Node node = node(NodeType.PARAM_MESSAGE, x, y);
        set(node, "Text", value);
        return node;
    }

    private static Node coordinate(int x, int y, String xValue, String yValue, String zValue) {
        Node node = node(NodeType.PARAM_COORDINATE, x, y);
        set(node, "X", xValue);
        set(node, "Y", yValue);
        set(node, "Z", zValue);
        return node;
    }

    private static void set(Node node, String parameterName, String value) {
        node.setParameterValueAndPropagate(parameterName, value);
    }

    private static void attach(Node host, Node parameter, int slotIndex) {
        if (!host.attachParameter(parameter, slotIndex)) {
            throw new IllegalStateException("Failed to attach " + parameter.getType() + " to " + host.getType());
        }
    }

    private static void attachSensor(Node host, Node sensor) {
        if (!host.attachSensor(sensor)) {
            throw new IllegalStateException("Failed to attach sensor " + sensor.getType() + " to " + host.getType());
        }
    }

    private static void attachAction(Node host, Node action) {
        if (!host.attachActionNode(action)) {
            throw new IllegalStateException("Failed to attach action " + action.getType() + " to " + host.getType());
        }
    }

    private static NodeConnection connection(Node output, Node input) {
        return connection(output, input, 0);
    }

    private static NodeConnection connection(Node output, Node input, int outputSocket) {
        return new NodeConnection(output, input, outputSocket, 0);
    }

    public record RestoreResult(boolean success, int restoredCount, List<String> restoredNames) {
    }

    record ExamplePreset(String name, List<Node> nodes, List<NodeConnection> connections) {
    }
}
