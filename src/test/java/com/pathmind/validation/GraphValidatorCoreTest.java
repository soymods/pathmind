package com.pathmind.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;
import com.pathmind.nodes.NodeType;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphValidatorCoreTest {
    @TempDir
    Path tempHome;

    private String originalUserHome;

    @BeforeEach
    void setUp() {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempHome.toString());
    }

    @AfterEach
    void tearDown() {
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void reportsMissingStartForEmptyGraph() {
        GraphValidationResult result = GraphValidator.validate(List.of(), List.of(), "Default", true, true);

        assertTrue(result.hasErrors());
        assertTrue(codes(result).contains("missing_start"));
    }

    @Test
    void reportsDuplicateInputConnections() {
        Node startA = new Node(NodeType.START, 0, 0);
        Node startB = new Node(NodeType.START, 0, 80);
        Node wait = new Node(NodeType.WAIT, 120, 0);

        GraphValidationResult result = GraphValidator.validate(
            List.of(startA, startB, wait),
            List.of(
                new NodeConnection(startA, wait, 0, 0),
                new NodeConnection(startB, wait, 0, 0)
            ),
            "Default",
            true,
            true
        );

        assertTrue(result.hasErrors());
        assertTrue(codes(result).contains("multiple_inputs"));
    }

    @Test
    void reportsUnreachableNodesButAllowsReachableSimpleChain() {
        Node start = new Node(NodeType.START, 0, 0);
        Node reachable = new Node(NodeType.WAIT, 120, 0);
        Node unreachable = new Node(NodeType.MESSAGE, 240, 0);

        GraphValidationResult result = GraphValidator.validate(
            List.of(start, reachable, unreachable),
            List.of(new NodeConnection(start, reachable, 0, 0)),
            "Default",
            true,
            true
        );

        Set<String> codes = codes(result);
        assertTrue(codes.contains("unreachable_node"));
        assertFalse(result.getIssues().stream()
            .anyMatch(issue -> "unreachable_node".equals(issue.getCode()) && reachable.getId().equals(issue.getNodeId())));
    }

    @Test
    void reportsUnavailableUiUtilsDependency() {
        Node start = new Node(NodeType.START, 0, 0);
        Node uiUtils = new Node(NodeType.UI_UTILS, 120, 0);

        GraphValidationResult result = GraphValidator.validate(
            List.of(start, uiUtils),
            List.of(new NodeConnection(start, uiUtils, 0, 0)),
            "Default",
            true,
            false
        );

        assertTrue(result.hasErrors());
        assertTrue(codes(result).contains("missing_ui_utils"));
    }

    private static Set<String> codes(GraphValidationResult result) {
        return result.getIssues().stream()
            .map(GraphValidationIssue::getCode)
            .collect(Collectors.toSet());
    }
}
