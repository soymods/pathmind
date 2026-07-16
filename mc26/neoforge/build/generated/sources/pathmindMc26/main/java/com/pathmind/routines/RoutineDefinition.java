package com.pathmind.routines;

import com.pathmind.nodes.Node;
import com.pathmind.nodes.NodeConnection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Live routine model with an embedded graph owned by one preset. */
public final class RoutineDefinition {
    private final String id;
    private final String name;
    private final int interfaceVersion;
    private final int implementationRevision;
    private final String interfaceSignature;
    private final String implementationSignature;
    private final List<RoutineInputDefinition> inputs;
    private final List<Node> nodes;
    private final List<NodeConnection> connections;

    public RoutineDefinition(String id, String name, int interfaceVersion, int implementationRevision,
                             String interfaceSignature, String implementationSignature,
                             List<RoutineInputDefinition> inputs, List<Node> nodes,
                             List<NodeConnection> connections) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.trim();
        this.name = name == null || name.isBlank() ? "Routine" : name.trim();
        this.interfaceVersion = Math.max(1, interfaceVersion);
        this.implementationRevision = Math.max(1, implementationRevision);
        this.interfaceSignature = interfaceSignature == null ? "" : interfaceSignature;
        this.implementationSignature = implementationSignature == null ? "" : implementationSignature;
        ArrayList<RoutineInputDefinition> orderedInputs = new ArrayList<>(inputs == null ? List.of() : inputs);
        orderedInputs.sort(Comparator.comparingInt(RoutineInputDefinition::getOrder));
        this.inputs = List.copyOf(orderedInputs);
        this.nodes = List.copyOf(nodes == null ? List.of() : nodes);
        this.connections = List.copyOf(connections == null ? List.of() : connections);
    }

    public static RoutineDefinition create(String name) {
        return new RoutineDefinition(null, name, 1, 1, "", "", List.of(), List.of(), List.of());
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getInterfaceVersion() { return interfaceVersion; }
    public int getImplementationRevision() { return implementationRevision; }
    public String getInterfaceSignature() { return interfaceSignature; }
    public String getImplementationSignature() { return implementationSignature; }
    public List<RoutineInputDefinition> getInputs() { return inputs; }
    public List<Node> getNodes() { return nodes; }
    public List<NodeConnection> getConnections() { return connections; }
}
